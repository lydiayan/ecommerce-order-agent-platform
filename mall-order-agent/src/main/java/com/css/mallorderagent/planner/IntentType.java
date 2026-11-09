package com.css.mallorderagent.planner;

/** 企业多角色 Agent 支持的统一意图集合；角色只影响授权和知识范围。 */
public enum IntentType {
    ORDER_QUERY,
    ORDER_POLICY_QUERY,
    SENSITIVE_ORDER_OPERATION,
    RAG_QA,
    UNKNOWN
}
