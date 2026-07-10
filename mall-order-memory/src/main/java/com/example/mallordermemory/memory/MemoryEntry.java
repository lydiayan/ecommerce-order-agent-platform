package com.example.mallordermemory.memory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆条目 POJO
 * <p>表示一条从对话中提取出的记忆，可存储于 Milvus 长期记忆库中。</p>
 */
public class MemoryEntry {

    /**
     * 主键 ID（Milvus 主键）
     */
    private String id;

    /**
     * 记忆类型
     */
    private MemoryType type;

    /**
     * 记忆内容文本
     */
    private String content;

    /**
     * 向量嵌入（用于语义检索）
     */
    private float[] embedding;

    /**
     * 关联的对话 ID
     */
    private String conversationId;

    /**
     * 来源用户标识
     */
    private String userId;

    /**
     * 重要性分数（0.0 ~ 1.0），用于优先级排序
     */
    private double importance;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 元数据（可扩展）
     */
    private Map<String, Object> metadata;

    /**
     * 语义搜索时的相似度得分
     */
    private double score;

    public MemoryEntry() {
    }

    public MemoryEntry(String id, MemoryType type, String content, String conversationId, String userId) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.conversationId = conversationId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
        this.importance = 0.5;
    }

    // --- getters / setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
