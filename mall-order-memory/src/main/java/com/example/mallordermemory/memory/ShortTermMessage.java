package com.example.mallordermemory.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Redis 短期记忆单条消息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortTermMessage {

    private String messageId;
    private String sessionId;
    private String role;
    private String content;
    private long createTime;

    public ShortTermMessage() {
    }

    public ShortTermMessage(String messageId, String sessionId, String role, String content, long createTime) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createTime = createTime;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
