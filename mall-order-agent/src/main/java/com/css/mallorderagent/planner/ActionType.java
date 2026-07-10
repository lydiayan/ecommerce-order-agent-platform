package com.css.mallorderagent.planner;

/**
 * 规划动作的能力类型，对应 Graph 节点或 Tool 的执行类别。
 */
public enum ActionType {

    /** 外部工具调用（MCP / Function Calling） */
    TOOL,

    /** 知识库检索（RAG） */
    RAG,

    /** 记忆加载（短期 / 长期 / 画像） */
    MEMORY,

    /** 大模型生成 */
    LLM
}
