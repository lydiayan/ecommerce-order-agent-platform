package com.example.mallordermemory.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 混合记忆管理器：协调 Redis 短期记忆与 Milvus 长期记忆。
 */
public class HybridMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(HybridMemoryManager.class);

    private final ShortTermMemoryManager shortTerm;
    private final LongTermMemoryManager longTerm;
    private final String defaultUserId;

    public HybridMemoryManager(ShortTermMemoryManager shortTerm,
                               LongTermMemoryManager longTerm,
                               String defaultUserId) {
        this.shortTerm = shortTerm;
        this.longTerm = longTerm;
        this.defaultUserId = defaultUserId;
    }

    public void addExchange(String userMessage, String assistantMessage) {
        shortTerm.addExchange(userMessage, assistantMessage);
    }

    public void addExchange(String sessionId, String userMessage, String assistantMessage) {
        shortTerm.addExchange(sessionId, userMessage, assistantMessage);
    }

    public void addExchange(String userId, String sessionId, String userMessage, String assistantMessage) {
        shortTerm.addExchange(userId, sessionId, userMessage, assistantMessage);
    }

    public List<Message> getRecentMessages(String sessionId, int count) {
        return shortTerm.getRecentMessages(sessionId, count);
    }

    public List<Message> getRecentMessages(String userId, String sessionId, int count) {
        return shortTerm.getRecentMessages(userId, sessionId, count);
    }

    public List<MemoryEntry> searchLongTerm(float[] queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || topK <= 0) {
            return List.of();
        }
        return longTerm.search(null, queryEmbedding, topK);
    }

    public String formatLongTermContext(List<MemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("--- 相关长期记忆 ---\n");
        for (MemoryEntry entry : entries) {
            context.append("[").append(entry.getType().getDisplayName()).append("] ")
                    .append(entry.getContent()).append("\n");
        }
        return context.toString().trim();
    }

    public String buildContext(String sessionId, float[] queryEmbedding) {
        StringBuilder context = new StringBuilder();
        String recent = shortTerm.getRecentConversation(sessionId, 10);
        if (!recent.isBlank()) {
            context.append("--- 近期对话 ---\n").append(recent).append("\n");
        }
        String longTermContext = formatLongTermContext(searchLongTerm(queryEmbedding, 5));
        if (!longTermContext.isBlank()) {
            context.append(longTermContext).append("\n");
        }
        return context.toString();
    }

    public MemoryEntry storeMemory(MemoryType type, String content,
                                   String userId, String sessionId,
                                   float[] embedding, double importance) {
        String normalizedContent = content != null ? content.trim() : "";
        if (type == MemoryType.USER_PROFILE) {
            log.debug("USER_PROFILE is stored in MySQL, skip Milvus store for sessionId={}", sessionId);
            return null;
        }
        String id = deterministicId(type, sessionId, normalizedContent);
        if (type == MemoryType.SUMMARY && longTerm.existsById(type, id)) {
            longTerm.delete(type, List.of(id));
            log.debug("Replacing existing summary for sessionId={}", sessionId);
        } else if (longTerm.existsById(type, id)) {
            log.debug("Skip duplicate {} memory for sessionId={}", type.getDisplayName(), sessionId);
            return null;
        }

        MemoryEntry entry = new MemoryEntry(
                id,
                type,
                normalizedContent,
                sessionId,
                userId != null ? userId : defaultUserId
        );
        entry.setEmbedding(embedding);
        entry.setImportance(importance);
        entry.setCreatedAt(LocalDateTime.now());

        if (!longTerm.store(entry)) {
            log.warn("Failed to persist {} memory for sessionId={}: {}",
                    type.getDisplayName(), sessionId, normalizedContent);
            return null;
        }
        log.info("Stored {} memory: {}", type.getDisplayName(), normalizedContent);
        return entry;
    }

    private static String deterministicId(MemoryType type, String sessionId, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = type == MemoryType.SUMMARY
                    ? type.name() + "|" + (sessionId != null ? sessionId : "") + "|summary"
                    : type.name() + "|" + (sessionId != null ? sessionId : "") + "|" + content;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    public void clearShortTerm(String sessionId) {
        shortTerm.clear(sessionId);
    }

    public ShortTermMemoryManager getShortTerm() {
        return shortTerm;
    }

    public LongTermMemoryManager getLongTerm() {
        return longTerm;
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }
}
