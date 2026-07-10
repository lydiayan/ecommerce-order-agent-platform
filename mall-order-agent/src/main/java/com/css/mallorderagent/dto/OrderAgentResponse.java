package com.css.mallorderagent.dto;

import com.example.mallordermilvusrag.dto.SearchResponse;

/**
 * 订单 Agent 问答响应。
 */
public class OrderAgentResponse {

    private String query;
    private String answer;
    private boolean grounded;
    private String traceId;
    private String conversationId;
    /** Planner 选定的策略，如 RAG_QA */
    private String planStrategy;
    private SearchResponse retrieval;

    /** Graph 是否在 human 节点前中断，等待人工审核 */
    private boolean interrupted;

    /** Graph checkpoint 线程 ID，resume 时需回传 */
    private String threadId;

    /** 中断提示信息 */
    private String interruptMessage;

    /** 敏感操作名称（如 退货、退款） */
    private String operationLabel;

    /** 审核原因说明 */
    private String approvalReason;

    /** 是否等待用户在对话中回复确认/取消（敏感订单操作） */
    private boolean awaitingUserConfirm;

    public boolean isAwaitingUserConfirm() {
        return awaitingUserConfirm;
    }

    public void setAwaitingUserConfirm(boolean awaitingUserConfirm) {
        this.awaitingUserConfirm = awaitingUserConfirm;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getPlanStrategy() {
        return planStrategy;
    }

    public void setPlanStrategy(String planStrategy) {
        this.planStrategy = planStrategy;
    }

    public SearchResponse getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(SearchResponse retrieval) {
        this.retrieval = retrieval;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getInterruptMessage() {
        return interruptMessage;
    }

    public void setInterruptMessage(String interruptMessage) {
        this.interruptMessage = interruptMessage;
    }

    public String getOperationLabel() {
        return operationLabel;
    }

    public void setOperationLabel(String operationLabel) {
        this.operationLabel = operationLabel;
    }

    public String getApprovalReason() {
        return approvalReason;
    }

    public void setApprovalReason(String approvalReason) {
        this.approvalReason = approvalReason;
    }
}
