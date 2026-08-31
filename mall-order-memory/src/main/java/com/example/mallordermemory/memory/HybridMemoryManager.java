package com.example.mallordermemory.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * 将一轮对话写入默认用户和默认会话的短期记忆。
     *
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    public void addExchange(String userMessage, String assistantMessage) {
        shortTerm.addExchange(userMessage, assistantMessage);
    }

    /**
     * 将一轮对话写入默认用户的指定短期会话。
     *
     * @param sessionId 会话编号
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    public void addExchange(String sessionId, String userMessage, String assistantMessage) {
        shortTerm.addExchange(sessionId, userMessage, assistantMessage);
    }

    /**
     * 将一轮对话写入指定用户会话的短期记忆。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    public void addExchange(String userId, String sessionId, String userMessage, String assistantMessage) {
        shortTerm.addExchange(userId, sessionId, userMessage, assistantMessage);
    }

    /**
     * 读取默认用户指定会话的最近短期消息。
     *
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return Spring AI 消息列表
     */
    public List<Message> getRecentMessages(String sessionId, int count) {
        return shortTerm.getRecentMessages(sessionId, count);
    }

    /**
     * 读取指定用户会话的最近短期消息。
     *
     * @param userId 用户编号
     * @param sessionId 会话编号
     * @param count 请求条数
     * @return Spring AI 消息列表
     */
    public List<Message> getRecentMessages(String userId, String sessionId, int count) {
        return shortTerm.getRecentMessages(userId, sessionId, count);
    }

    /**
     * 在默认用户范围内检索长期记忆。
     *
     * @param queryEmbedding 查询向量
     * @param topK 最大结果数
     * @return 按相似度排序的长期记忆
     */
    public List<MemoryEntry> searchLongTerm(float[] queryEmbedding, int topK) {
        return searchLongTerm(defaultUserId, queryEmbedding, topK);
    }

    /**
     * 在指定用户范围内跨记忆类型检索，防止不同用户的长期记忆混用。
     *
     * @param userId 用户编号；为空时使用默认用户
     * @param queryEmbedding 查询向量
     * @param topK 最大结果数
     * @return 按相似度排序的长期记忆
     */
    public List<MemoryEntry> searchLongTerm(String userId, float[] queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || topK <= 0) {
            return List.of();
        }
        return longTerm.search(null, userId != null ? userId : defaultUserId, queryEmbedding, topK);
    }

    /**
     * 将长期记忆按类型标记后格式化为提示词上下文。
     *
     * @param entries 长期记忆条目
     * @return 上下文文本；无条目时返回空字符串
     */
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

    /**
     * 组合默认用户的近期会话和相关长期记忆，供 Agent 提示词使用。
     *
     * @param sessionId 会话编号
     * @param queryEmbedding 当前查询向量
     * @return 混合记忆上下文
     */
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

    /**
     * 以确定性编号幂等写入用户长期记忆。用户画像不会写入 Milvus；摘要会替换
     * 同一用户会话的旧摘要，其他重复内容会被忽略。
     *
     * @param type 记忆类型
     * @param content 记忆内容
     * @param userId 用户编号；为空时使用默认用户
     * @param sessionId 来源会话编号
     * @param embedding 内容向量
     * @param importance 重要度或抽取置信度
     * @return 已保存条目；不适用、重复或写入失败时返回 {@code null}
     */
    public MemoryEntry storeMemory(MemoryType type, String content,
                                   String userId, String sessionId,
                                   float[] embedding, double importance) {
        String normalizedContent = content != null ? content.trim() : "";
        if (type == MemoryType.USER_PROFILE) {
            log.debug("USER_PROFILE is stored in MySQL, skip Milvus store for sessionId={}", sessionId);
            return null;
        }
        String resolvedUserId = userId != null ? userId : defaultUserId;
        String id = deterministicId(type, resolvedUserId, sessionId, normalizedContent);
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
                resolvedUserId
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

    private static String deterministicId(MemoryType type, String userId, String sessionId, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = type == MemoryType.SUMMARY
                    ? type.name() + "|" + userId + "|" + (sessionId != null ? sessionId : "") + "|summary"
                    : type.name() + "|" + userId + "|" + (sessionId != null ? sessionId : "") + "|" + content;
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

    /**
     * 清除默认用户指定会话的短期记忆。
     *
     * @param sessionId 会话编号
     */
    public void clearShortTerm(String sessionId) {
        shortTerm.clear(sessionId);
    }

    /**
     * 批量清除目标用户的全部短期会话和长期向量记忆。
     *
     * @param userIds 用户编号集合；空值会被忽略
     */
    public void clearUsers(Collection<String> userIds) {
        Set<String> targets = new HashSet<>();
        if (userIds != null) {
            userIds.stream().filter(id -> id != null && !id.isBlank())
                    .map(String::trim).forEach(targets::add);
        }
        if (targets.isEmpty()) {
            return;
        }
        shortTerm.listSessions().stream()
                .filter(session -> targets.contains(session.userId()))
                .forEach(session -> shortTerm.clear(session.userId(), session.sessionId()));
        longTerm.deleteByUsers(List.copyOf(targets));
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
