package com.example.mallordermilvusrag.dto;

import java.time.LocalDateTime;

/** MySQL-owned summary for one logical knowledge document. Chunk content remains in Milvus. */
public record KnowledgeDocumentRecord(
        String filename,
        DocumentMetadata metadata,
        String documentId,
        String strategy,
        String contentType,
        int chunkCount,
        int averageTokenCount,
        int maxTokenCount,
        int overlapTokenCount,
        long originalFileSize,
        String fileSha256,
        KnowledgeImportStatus importStatus,
        String lastError,
        LocalDateTime importedAt,
        LocalDateTime updatedAt
) {
    public KnowledgeDocumentRecord withStatus(KnowledgeImportStatus status, String error,
                                              LocalDateTime completedAt) {
        return new KnowledgeDocumentRecord(filename, metadata, documentId, strategy, contentType,
                chunkCount, averageTokenCount, maxTokenCount, overlapTokenCount, originalFileSize,
                fileSha256, status, error, completedAt, updatedAt);
    }
}
