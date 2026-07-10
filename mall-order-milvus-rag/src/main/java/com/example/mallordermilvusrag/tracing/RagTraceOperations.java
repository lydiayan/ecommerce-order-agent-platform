package com.example.mallordermilvusrag.tracing;

/**
 * RAG 链路 trace 节点名称常量。
 */
public final class RagTraceOperations {

    public static final String RETRIEVE = "retrieve";
    public static final String EMBED = "embed";
    public static final String MILVUS = "milvus";
    public static final String RERANK = "rerank";
    public static final String PROMPT_BUILD = "prompt_build";
    public static final String LLM = "llm";

    private RagTraceOperations() {
    }
}
