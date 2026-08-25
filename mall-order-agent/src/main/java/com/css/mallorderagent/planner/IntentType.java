package com.css.mallorderagent.planner;

/** 订单 Agent 第一阶段支持的意图集合。 */
public enum IntentType {
    ORDER_QUERY,
    ORDER_POLICY_QUERY,
    SENSITIVE_ORDER_OPERATION,
    RAG_QA,
    UNKNOWN
}
