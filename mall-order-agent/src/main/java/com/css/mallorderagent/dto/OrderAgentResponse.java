package com.css.mallorderagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.mallordermilvusrag.dto.SearchResponse;

/**
 * 订单 Agent 问答响应。
 */
public class OrderAgentResponse {

    private String responseId;
    private boolean feedbackEnabled;
    private String query;
    private String answer;
    private boolean grounded;
    private String traceId;
    private String conversationId;
    /** Planner 选定的策略，如 RAG_QA */
    private String planStrategy;
    private String intent;
    private String intentSource;
    private double intentConfidence;
    private String ruleMatchStatus;
    private boolean clarificationRequired;
    private SearchResponse retrieval;

    /** Internal diagnostic snapshot; never returned to end users. */
    private String toolSummary;

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

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public boolean isFeedbackEnabled() {
        return feedbackEnabled;
    }

    public void setFeedbackEnabled(boolean feedbackEnabled) {
        this.feedbackEnabled = feedbackEnabled;
    }

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

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getIntentSource() {
        return intentSource;
    }

    public void setIntentSource(String intentSource) {
        this.intentSource = intentSource;
    }

    public double getIntentConfidence() {
        return intentConfidence;
    }

    public void setIntentConfidence(double intentConfidence) {
        this.intentConfidence = intentConfidence;
    }

    public String getRuleMatchStatus() {
        return ruleMatchStatus;
    }

    public void setRuleMatchStatus(String ruleMatchStatus) {
        this.ruleMatchStatus = ruleMatchStatus;
    }

    public boolean isClarificationRequired() {
        return clarificationRequired;
    }

    public void setClarificationRequired(boolean clarificationRequired) {
        this.clarificationRequired = clarificationRequired;
    }

    public SearchResponse getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(SearchResponse retrieval) {
        this.retrieval = retrieval;
    }

    @JsonIgnore
    public String getToolSummary() {
        return toolSummary;
    }

    public void setToolSummary(String toolSummary) {
        this.toolSummary = toolSummary;
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
