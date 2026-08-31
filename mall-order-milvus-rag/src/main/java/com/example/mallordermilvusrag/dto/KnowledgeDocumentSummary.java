package com.example.mallordermilvusrag.dto;

import java.time.LocalDateTime;

/** Catalog metadata combined with the current state found in Milvus. */
public record KnowledgeDocumentSummary(
        String filename,
        DocumentMetadata metadata,
        String documentId,
        int chunkCount,
        String strategy,
        String contentType,
        int averageTokenCount,
        int maxTokenCount,
        int overlapTokenCount,
        long originalFileSize,
        String fileSha256,
        String importStatus,
        String lastError,
        LocalDateTime importedAt,
        LocalDateTime updatedAt
) {
}
