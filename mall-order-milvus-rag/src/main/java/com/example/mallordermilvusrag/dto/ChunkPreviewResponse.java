package com.example.mallordermilvusrag.dto;

import java.util.List;

/** Metrics and exact chunk output produced by the configured splitter. */
public record ChunkPreviewResponse(
        String filename,
        DocumentMetadata metadata,
        String documentId,
        String strategy,
        String contentType,
        int chunkCount,
        int averageTokenCount,
        int maxTokenCount,
        int overlapTokens,
        List<KnowledgeChunkView> chunks
) {
}
