package com.example.mallordermilvusrag.splitter.model;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;

import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 切分完成后的不可变 Chunk。
 *
 * <p>{@code startOffset/endOffset} 默认指向原文的 Java 字符偏移；如果输入经过 HTML 等
 * 归一化，元数据中的 {@link RagChunkMetadata#OFFSET_BASIS} 会标明实际偏移基准。</p>
 *
 * @param content     Chunk 正文
 * @param documentId  所属文档的稳定 ID
 * @param chunkId     当前 Chunk 的稳定 ID
 * @param parentId    子块所属父块 ID；独立块和父块为空
 * @param chunkLevel  独立、父或子层级
 * @param chunkIndex  当前 Chunk 在本次切分结果中的下标
 * @param totalChunks 本次切分产生的 Chunk 总数
 * @param strategy    实际执行的切分策略
 * @param contentType 归一化后的文档内容类型
 * @param titlePath   结构化文档中的层级标题路径
 * @param startOffset 正文起始位置，包含该字符
 * @param endOffset   正文结束位置，不包含该字符
 * @param tokenCount  正文 Token 数
 * @param metadata    业务元数据及算法补充元数据
 */
public record RagChunk(
        String content,
        String documentId,
        String chunkId,
        String parentId,
        ChunkLevel chunkLevel,
        int chunkIndex,
        int totalChunks,
        RagSplitStrategy strategy,
        RagContentType contentType,
        String titlePath,
        int startOffset,
        int endOffset,
        int tokenCount,
        Map<String, Object> metadata
) {
    public RagChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    /**
     * 转为 Spring AI 文档，供 Embedding 和 Milvus 写入流程使用。
     * 标准字段后写入，因此不会被同名业务元数据覆盖。
     */
    public Document toDocument() {
        Map<String, Object> values = new LinkedHashMap<>(metadata);
        values.put(RagChunkMetadata.DOCUMENT_ID, documentId);
        values.put(RagChunkMetadata.CHUNK_ID, chunkId);
        values.put(RagChunkMetadata.PARENT_ID, parentId == null ? "" : parentId);
        values.put(RagChunkMetadata.CHUNK_LEVEL, chunkLevel.name());
        values.put(RagChunkMetadata.CHUNK_INDEX, chunkIndex);
        values.put(RagChunkMetadata.TOTAL_CHUNKS, totalChunks);
        values.put(RagChunkMetadata.STRATEGY, strategy.name());
        values.put(RagChunkMetadata.CONTENT_TYPE, contentType.name());
        values.put(RagChunkMetadata.TITLE_PATH, titlePath == null ? "" : titlePath);
        values.put(RagChunkMetadata.START_OFFSET, startOffset);
        values.put(RagChunkMetadata.END_OFFSET, endOffset);
        values.put(RagChunkMetadata.TOKEN_COUNT, tokenCount);
        return new Document(chunkId, content, values);
    }
}
