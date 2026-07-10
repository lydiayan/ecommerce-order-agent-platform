package com.css.mallorderagent.dto;

/**
 * 人工审核反馈：用于 resume 中断的 Graph 执行。
 */
public class HumanFeedbackRequest {

    /** Graph checkpoint 线程 ID，与 ask 时的 conversationId 一致 */
    private String threadId;

    /** 是否通过审核 */
    private Boolean approved;

    /** 驳回后重写的问题（approved=false 时可选） */
    private String revisedQuery;

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRevisedQuery() {
        return revisedQuery;
    }

    public void setRevisedQuery(String revisedQuery) {
        this.revisedQuery = revisedQuery;
    }
}
