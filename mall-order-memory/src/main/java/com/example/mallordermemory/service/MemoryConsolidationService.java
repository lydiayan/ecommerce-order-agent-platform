package com.example.mallordermemory.service;

import com.example.mallordermemory.config.MemoryProperties;
import com.example.mallordermemory.memory.ConsolidationCursor;
import com.example.mallordermemory.memory.HybridMemoryManager;
import com.example.mallordermemory.memory.MemoryEntry;
import com.example.mallordermemory.memory.MemorySessionKey;
import com.example.mallordermemory.memory.MemoryType;
import com.example.mallordermemory.memory.ShortTermMessage;
import com.example.mallordermemory.memory.MemoryType;
import com.example.mallordermemory.service.MemoryExtractor.ExtractedMemory;
import com.example.mallordermemory.service.UserProfileMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 记忆合并服务：按增量消息（Token 或条数）触发，仅合并 lastMessageId 之后的新消息。
 */
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private final HybridMemoryManager hybridMemoryManager;
    private final MemoryExtractor memoryExtractor;
    private final EmbeddingModel embeddingModel;
    private final String defaultUserId;
    private final String defaultSessionId;
    private final int minMessagesForConsolidation;
    private final int messageThreshold;
    private final int tokenThreshold;

    private final Optional<UserProfileMergeService> userProfileMergeService;

    public MemoryConsolidationService(HybridMemoryManager hybridMemoryManager,
                                      MemoryExtractor memoryExtractor,
                                      EmbeddingModel embeddingModel,
                                      MemoryProperties properties,
                                      Optional<UserProfileMergeService> userProfileMergeService) {
        this.hybridMemoryManager = hybridMemoryManager;
        this.memoryExtractor = memoryExtractor;
        this.embeddingModel = embeddingModel;
        this.defaultUserId = properties.getUserId();
        this.defaultSessionId = properties.getConversationId();
        MemoryProperties.ConsolidationProperties consolidation = properties.getConsolidation();
        this.minMessagesForConsolidation = consolidation.getMinMessages();
        this.messageThreshold = consolidation.getMessageThreshold();
        this.tokenThreshold = consolidation.getTokenThreshold();
        this.userProfileMergeService = userProfileMergeService;
    }

    @Scheduled(fixedDelayString = "#{@memoryConsolidationProperties.intervalMs}")
    public void scheduledConsolidation() {
        log.debug("scheduledConsolidation tick");
        List<MemorySessionKey> sessions = hybridMemoryManager.getShortTerm().listSessions();
        if (sessions.isEmpty()) {
            sessions = List.of(MemorySessionKey.of(defaultUserId, defaultSessionId));
        }

        boolean consolidatedAny = false;
        for (MemorySessionKey session : sessions) {
            if (!shouldConsolidate(session)) {
                continue;
            }
            try {
                int stored = consolidate(session.userId(), session.sessionId());
                if (stored > 0) {
                    consolidatedAny = true;
                }
            } catch (Exception e) {
                log.error("Scheduled consolidation failed for session={}: {}",
                        session.redisKeySuffix(), e.getMessage(), e);
            }
        }

        if (!consolidatedAny) {
            log.debug("No session eligible for consolidation this tick, checked={}", sessions.size());
        }
    }

    public void consolidate() {
        consolidate(defaultUserId, defaultSessionId);
    }

    public void consolidate(String sessionId) {
        consolidate(defaultUserId, sessionId);
    }

    /**
     * @return 实际新写入条数；无增量消息时返回 0
     */
    public int consolidate(String userId, String sessionId) {
        List<ShortTermMessage> delta = hybridMemoryManager.getShortTerm()
                .listMessagesAfter(userId, sessionId, getLastMessageId(userId, sessionId));
        if (delta.isEmpty()) {
            log.info("No new messages to consolidate for session={}:{}", userId, sessionId);
            resetPendingIfNeeded(userId, sessionId);
            return 0;
        }

        String conversationText = hybridMemoryManager.getShortTerm().formatConversationText(delta);
        List<ExtractedMemory> extracted = memoryExtractor.extract(conversationText);
        if (extracted.isEmpty()) {
            log.info("No structured memories extracted for session={}:{}", userId, sessionId);
        }

        List<String> profileHints = new ArrayList<>();
        List<ExtractedMemory> milvusMemories = new ArrayList<>();
        for (ExtractedMemory mem : extracted) {
            if (mem.getType() == MemoryType.USER_PROFILE) {
                profileHints.add(mem.getContent());
            } else {
                milvusMemories.add(mem);
            }
        }

        int storedCount = 0;
        if (userProfileMergeService.isPresent()) {
            boolean profileMerged = userProfileMergeService.get().mergeFromConversation(
                    userId, sessionId, conversationText, profileHints);
            if (profileMerged) {
                storedCount++;
            }
        }

        for (ExtractedMemory mem : milvusMemories) {
            try {
                float[] embedding = embeddingModel.embed(new Document(mem.getContent()));
                MemoryEntry stored = hybridMemoryManager.storeMemory(
                        mem.getType(),
                        mem.getContent(),
                        userId,
                        sessionId,
                        embedding,
                        mem.getConfidence()
                );
                if (stored != null) {
                    storedCount++;
                    log.info("Consolidated {} memory for session={}: {}: {}",
                            mem.getType().getDisplayName(), sessionId, userId, mem.getContent());
                }
            } catch (Exception e) {
                log.warn("Failed to consolidate memory '{}' for session={}: {}",
                        mem.getContent(), sessionId, e.getMessage());
            }
        }

        markConsolidated(userId, sessionId, delta.get(delta.size() - 1).getMessageId());
        log.info("Consolidation completed for session={}:{}: {} new memories stored, lastMessageId={}",
                userId, sessionId, storedCount, delta.get(delta.size() - 1).getMessageId());
        return storedCount;
    }

    public boolean shouldConsolidate(MemorySessionKey session) {
        int messageCount = hybridMemoryManager.getShortTerm().getMessageCount(session.userId(), session.sessionId());
        if (messageCount < minMessagesForConsolidation) {
            return false;
        }
        ConsolidationCursor cursor = hybridMemoryManager.getShortTerm()
                .getConsolidationCursor(session.userId(), session.sessionId());
        return cursor.getPendingMessages() >= messageThreshold
                || cursor.getPendingTokens() >= tokenThreshold;
    }

    private String getLastMessageId(String userId, String sessionId) {
        return hybridMemoryManager.getShortTerm().getConsolidationCursor(userId, sessionId).getLastMessageId();
    }

    private void markConsolidated(String userId, String sessionId, String lastMessageId) {
        ConsolidationCursor cursor = hybridMemoryManager.getShortTerm().getConsolidationCursor(userId, sessionId);
        cursor.setLastMessageId(lastMessageId);
        cursor.resetPending();
        hybridMemoryManager.getShortTerm().saveConsolidationCursor(userId, sessionId, cursor);
    }

    private void resetPendingIfNeeded(String userId, String sessionId) {
        ConsolidationCursor cursor = hybridMemoryManager.getShortTerm().getConsolidationCursor(userId, sessionId);
        if (cursor.getPendingMessages() > 0 || cursor.getPendingTokens() > 0) {
            cursor.resetPending();
            hybridMemoryManager.getShortTerm().saveConsolidationCursor(userId, sessionId, cursor);
        }
    }
}

