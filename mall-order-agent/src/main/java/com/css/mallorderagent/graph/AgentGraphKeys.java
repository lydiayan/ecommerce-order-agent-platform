package com.css.mallorderagent.graph;

/**
 * Agent Graph 共享状态键。
 */
public final class AgentGraphKeys {

    public static final String ASK_REQUEST = "askRequest";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String QUERY = "query";
    public static final String PERSONA_CONTEXT = "personaContext";
    public static final String CAPABILITIES = "capabilities";
    public static final String AUTHORIZED_CUSTOMER_IDS = "authorizedCustomerIds";
    public static final String RAG_ROLE_SCOPES = "ragRoleScopes";
    public static final String RAG_DEPARTMENT_SCOPES = "ragDepartmentScopes";

    /** 当前请求对应的短期 SSE 会话；只在流式 HTTP 接口中存在 */
    public static final String STREAM_ID = "streamId";

    public static final String HISTORY = "history";
    public static final String HISTORY_COUNT = "historyCount";
    public static final String USER_PROFILE_CONTEXT = "userProfileContext";
    public static final String LONG_TERM_MEMORY = "longTermMemory";
    public static final String MEMORY_COUNT = "memoryCount";

    public static final String RETRIEVAL = "retrieval";
    public static final String CONTEXT = "context";
    public static final String CONTEXT_HIT_COUNT = "contextHitCount";
    public static final String GROUNDED = "grounded";

    public static final String PLAN = "plan";
    public static final String PLAN_STRATEGY = "planStrategy";
    public static final String INTENT = "intent";
    public static final String INTENT_SOURCE = "intentSource";
    public static final String INTENT_CONFIDENCE = "intentConfidence";
    public static final String RULE_MATCH_STATUS = "ruleMatchStatus";
    public static final String CLARIFICATION_REQUIRED = "clarificationRequired";
    public static final String BUILT_PROMPT = "builtPrompt";

    public static final String TOOL_RESULT = "toolResult";

    public static final String ANSWER = "answer";
    public static final String HUMAN_FEEDBACK = "humanFeedback";
    public static final String NEXT_NODE = "nextNode";

    /** 是否启用人工审核（由 OrderAgentService 写入初始状态） */
    public static final String HUMAN_REVIEW_ENABLED = "humanReviewEnabled";

    /** 当前轮次是否需要人工审核（由 Planner 根据敏感操作判定） */
    public static final String HUMAN_APPROVAL_REQUIRED = "humanApprovalRequired";

    /** 人工审核原因（展示给审核人） */
    public static final String APPROVAL_REASON = "approvalReason";

    private AgentGraphKeys() {
    }
}
