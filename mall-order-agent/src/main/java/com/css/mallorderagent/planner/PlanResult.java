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

    public boolean humanApprovalRequired() {
        return humanApprovalRequired;
    }

    public String approvalReason() {
        return approvalReason;
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
