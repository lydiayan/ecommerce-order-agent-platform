package com.css.mallorderagent.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Planner 输出的单步动作定义。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionDefinition {

    private String action;
    private ActionType type;
    private String executor;

    public ActionDefinition() {
    }

    public ActionDefinition(String action, ActionType type, String executor) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("executor must not be blank");
        }
        this.action = action;
        this.type = type;
        this.executor = executor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String action() {
        return action;
    }

    public ActionType type() {
        return type;
    }

    public String executor() {
        return executor;
    }
}
