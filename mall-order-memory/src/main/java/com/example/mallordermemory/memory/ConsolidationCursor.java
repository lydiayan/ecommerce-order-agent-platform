package com.example.mallordermemory.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 合并游标：记录上次已合并到的 messageId 及待合并增量统计。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsolidationCursor {

    private String lastMessageId;
    private int pendingMessages;
    private int pendingTokens;

    public ConsolidationCursor() {
    }

    public ConsolidationCursor(String lastMessageId, int pendingMessages, int pendingTokens) {
        this.lastMessageId = lastMessageId;
        this.pendingMessages = pendingMessages;
        this.pendingTokens = pendingTokens;
    }

    /**
     * 创建尚未合并任何消息的空游标。
     *
     * @return 空游标
     */
    public static ConsolidationCursor empty() {
        return new ConsolidationCursor(null, 0, 0);
    }

    public String getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(String lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public int getPendingMessages() {
        return pendingMessages;
    }

    public void setPendingMessages(int pendingMessages) {
        this.pendingMessages = pendingMessages;
    }

    public int getPendingTokens() {
        return pendingTokens;
    }

    public void setPendingTokens(int pendingTokens) {
        this.pendingTokens = pendingTokens;
    }

    /**
     * 累加自上次合并以来的消息数和估算 Token 数。
     *
     * @param messages 新增消息数
     * @param tokens 新增估算 Token 数
     */
    public void addPending(int messages, int tokens) {
        this.pendingMessages += messages;
        this.pendingTokens += tokens;
    }

    /**
     * 合并成功后清零待处理统计，保留最后消息游标。
     */
    public void resetPending() {
        this.pendingMessages = 0;
        this.pendingTokens = 0;
    }
}
