package com.css.mallorderagent.planner;

import java.util.List;

/**
 * 常用 {@link ActionDefinition} 工厂与常量。
 */
public final class ActionDefinitions {

    public static final String KNOWLEDGE_SEARCH = "KNOWLEDGE_SEARCH";
    public static final String MEMORY_LOAD = "MEMORY_LOAD";
    public static final String LLM_GENERATE = "LLM_GENERATE";
    public static final String ORDER_QUERY = "ORDER_QUERY";
    public static final String REFUND_ELIGIBILITY = "REFUND_ELIGIBILITY";

    private ActionDefinitions() {
    }

    /** @return 加载会话与长期记忆的动作定义 */
    public static ActionDefinition memoryLoad() {
        return new ActionDefinition(MEMORY_LOAD, ActionType.MEMORY, "memoryNode");
    }

    /** @return 执行知识检索的动作定义 */
    public static ActionDefinition ragKnowledgeSearch() {
        return new ActionDefinition(KNOWLEDGE_SEARCH, ActionType.RAG, "retrieveNode");
    }

    /** @return 调用 LLM 生成回答的动作定义 */
    public static ActionDefinition llmGenerate() {
        return new ActionDefinition(LLM_GENERATE, ActionType.LLM, "llmNode");
    }

    /** @return 查询授权订单的工具动作定义 */
    public static ActionDefinition orderQueryTool() {
        return new ActionDefinition(ORDER_QUERY, ActionType.TOOL, "orderQueryTool");
    }

    /** @return 调用权威退款资格服务的工具动作定义 */
    public static ActionDefinition refundEligibilityTool() {
        return new ActionDefinition(REFUND_ELIGIBILITY, ActionType.TOOL, "refundEligibilityTool");
    }

    /** @return 标准 RAG 问答链路：记忆、检索、生成 */
    public static List<ActionDefinition> ragQaPipeline() {
        return List.of(memoryLoad(), ragKnowledgeSearch(), llmGenerate());
    }

    /** @return 订单查询链路：记忆、订单工具、生成 */
    public static List<ActionDefinition> orderQueryPipeline() {
        return List.of(memoryLoad(), orderQueryTool(), llmGenerate());
    }

    /** @return 订单退款资格链路：记忆、权威规则工具、生成 */
    public static List<ActionDefinition> orderPolicyQueryPipeline() {
        return List.of(memoryLoad(), refundEligibilityTool(), llmGenerate());
    }

    /** @return 敏感订单操作确认链路：记忆、查单、RAG 规则、确认话术 */
    public static List<ActionDefinition> dangerousOrderPipeline() {
        return List.of(memoryLoad(), orderQueryTool(), ragKnowledgeSearch(), llmGenerate());
    }
}
