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

    private ActionDefinitions() {
    }

    public static ActionDefinition memoryLoad() {
        return new ActionDefinition(MEMORY_LOAD, ActionType.MEMORY, "memoryNode");
    }

    public static ActionDefinition ragKnowledgeSearch() {
        return new ActionDefinition(KNOWLEDGE_SEARCH, ActionType.RAG, "retrieveNode");
    }

    public static ActionDefinition llmGenerate() {
        return new ActionDefinition(LLM_GENERATE, ActionType.LLM, "llmNode");
    }

    public static ActionDefinition orderQueryTool() {
        return new ActionDefinition(ORDER_QUERY, ActionType.TOOL, "orderQueryTool");
    }

    /** 标准 RAG 问答链路：记忆 → 检索 → 生成 */
    public static List<ActionDefinition> ragQaPipeline() {
        return List.of(memoryLoad(), ragKnowledgeSearch(), llmGenerate());
    }

    /** 订单查询链路：记忆 → 工具 → 生成 */
    public static List<ActionDefinition> orderQueryPipeline() {
        return List.of(memoryLoad(), orderQueryTool(), llmGenerate());
    }

    /** 敏感订单操作：记忆 → 查单 → RAG 规则 → 生成确认话术 */
    public static List<ActionDefinition> dangerousOrderPipeline() {
        return List.of(memoryLoad(), orderQueryTool(), ragKnowledgeSearch(), llmGenerate());
    }
}
