package com.css.mallorderagent.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner 输出：策略名称 + 有序动作列表。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanResult {

    private String strategy;
    private List<ActionDefinition> actions = new ArrayList<>();
    /** 是否需要人工审核（敏感操作如退货/付款/删除） */
    private boolean humanApprovalRequired;
    /** 人工审核原因说明（展示给审核人） */
    private String approvalReason;
    /** 归一化意图，如 ORDER_QUERY */
    private String intent;
    /** 最终分类来源：RULE、LLM 或 FALLBACK */
    private String intentSource;
    private double intentConfidence;
    private String ruleMatchStatus;
    private boolean clarificationRequired;
    /** 仅记录稳定原因码，不记录模型推理内容。 */
    private String classificationFallbackReason;
    private String clarificationMessage;

    public PlanResult() {
    }

    public PlanResult(String strategy, List<ActionDefinition> actions) {
        this(strategy, actions, false, null);
    }

    public PlanResult(String strategy, List<ActionDefinition> actions,
                      boolean humanApprovalRequired, String approvalReason) {
        this.strategy = strategy;
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        this.humanApprovalRequired = humanApprovalRequired;
        this.approvalReason = approvalReason;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<ActionDefinition> getActions() {
        return actions;
    }

    public void setActions(List<ActionDefinition> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public boolean isHumanApprovalRequired() {
        return humanApprovalRequired;
    }

    public void setHumanApprovalRequired(boolean humanApprovalRequired) {
        this.humanApprovalRequired = humanApprovalRequired;
    }

    public String getApprovalReason() {
        return approvalReason;
    }

    public void setApprovalReason(String approvalReason) {
        this.approvalReason = approvalReason;
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

    public String getClassificationFallbackReason() {
        return classificationFallbackReason;
    }

    public void setClassificationFallbackReason(String classificationFallbackReason) {
        this.classificationFallbackReason = classificationFallbackReason;
    }

    public String getClarificationMessage() {
        return clarificationMessage;
    }

    public void setClarificationMessage(String clarificationMessage) {
        this.clarificationMessage = clarificationMessage;
    }

    public boolean humanApprovalRequired() {
        return humanApprovalRequired;
    }

    public String approvalReason() {
        return approvalReason;
    }

    public String intent() {
        return intent;
    }

    public String intentSource() {
        return intentSource;
    }

    public double intentConfidence() {
        return intentConfidence;
    }

    public String ruleMatchStatus() {
        return ruleMatchStatus;
    }

    public boolean clarificationRequired() {
        return clarificationRequired;
    }

    public String classificationFallbackReason() {
        return classificationFallbackReason;
    }

    public String clarificationMessage() {
        return clarificationMessage;
    }

    public String strategy() {
        return strategy;
    }

    public List<ActionDefinition> actions() {
        return actions;
    }

    public boolean needRag() {
        return hasType(ActionType.RAG);
    }

    public boolean needLlm() {
        return hasType(ActionType.LLM);
    }

    public boolean needMemory() {
        return hasType(ActionType.MEMORY);
    }

    public boolean hasType(ActionType type) {
        return actions.stream().anyMatch(action -> action.type() == type);
    }

    public boolean hasAction(String action) {
        return actions.stream().anyMatch(def -> action.equals(def.action()));
    }

    public List<ActionDefinition> actionsOfType(ActionType type) {
        return actions.stream().filter(action -> action.type() == type).toList();
    }
}
