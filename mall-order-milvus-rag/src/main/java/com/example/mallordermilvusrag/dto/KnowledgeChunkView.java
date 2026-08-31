package com.example.mallordermilvusrag.dto;

/** A chunk shape shared by live preview and already-persisted knowledge documents. */
public record KnowledgeChunkView(
        String content,
        String documentId,
        String chunkId,
        String parentId,
        String chunkLevel,
        int chunkIndex,
        int totalChunks,
        String strategy,
        String contentType,
        String titlePath,
        long startOffset,
        long endOffset,
        int tokenCount,
        String offsetBasis,
        boolean splitDegraded,
        String splitDegradedReason
) {
}
