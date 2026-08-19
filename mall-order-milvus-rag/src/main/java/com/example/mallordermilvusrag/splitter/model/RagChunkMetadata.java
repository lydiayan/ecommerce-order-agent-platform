package com.example.mallordermilvusrag.splitter.model;

/**
 * 写入 Spring AI {@code Document.metadata} 的标准键名。
 *
 * <p>集中定义键名可以避免写入 Milvus和查询结果解析时出现拼写不一致。</p>
 */
public final class RagChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String CHUNK_ID = "chunk_id";
    public static final String PARENT_ID = "parent_id";
    public static final String CHUNK_LEVEL = "chunk_level";
    public static final String CHUNK_INDEX = "chunk_index";
    public static final String TOTAL_CHUNKS = "total_chunks";
    public static final String STRATEGY = "strategy";
    public static final String CONTENT_TYPE = "content_type";
    public static final String TITLE_PATH = "title_path";
    public static final String START_OFFSET = "start_offset";
    public static final String END_OFFSET = "end_offset";
    public static final String TOKEN_COUNT = "token_count";
    public static final String OFFSET_BASIS = "offset_basis";
    public static final String SPLIT_DEGRADED = "split_degraded";
    public static final String SPLIT_DEGRADED_REASON = "split_degraded_reason";

    private RagChunkMetadata() {
    }
}
