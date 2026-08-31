package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class KnowledgeChunkMetrics {

    private final RagSplitterProperties splitterProperties;

    public KnowledgeChunkMetrics(RagSplitterProperties splitterProperties) {
        this.splitterProperties = splitterProperties;
    }

    public Metrics fromDocuments(List<Document> documents) {
        Map<String, Object> first = documents.isEmpty() ? Map.of() : documents.get(0).getMetadata();
        String strategy = stringValue(first.get(RagChunkMetadata.STRATEGY),
                splitterProperties.getStrategy().name());
        String contentType = stringValue(first.get(RagChunkMetadata.CONTENT_TYPE), "PDF");
        String documentId = stringValue(first.get(RagChunkMetadata.DOCUMENT_ID), "");
        int totalTokens = documents.stream()
                .mapToInt(document -> intValue(document.getMetadata().get(RagChunkMetadata.TOKEN_COUNT)))
                .sum();
        int maxTokens = documents.stream()
                .mapToInt(document -> intValue(document.getMetadata().get(RagChunkMetadata.TOKEN_COUNT)))
                .max().orElse(0);
        int averageTokens = documents.isEmpty() ? 0 : Math.round((float) totalTokens / documents.size());
        return new Metrics(documentId, strategy, contentType, documents.size(), averageTokens,
                maxTokens, overlapTokens(strategy));
    }

    public int overlapTokens(String strategyName) {
        RagSplitStrategy strategy;
        try {
            strategy = RagSplitStrategy.valueOf(strategyName);
        } catch (RuntimeException ignored) {
            return 0;
        }
        return switch (strategy) {
            case SLIDING_WINDOW -> splitterProperties.getSlidingWindow().getOverlapTokens();
            case RECURSIVE, STRUCTURE_AWARE, CONTENT_TYPE_AWARE ->
                    splitterProperties.getRecursive().getOverlapTokens();
            case PARENT_CHILD -> splitterProperties.getParentChild().getChildOverlapTokens();
            case FIXED_SIZE, SEMANTIC -> 0;
        };
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || value.toString().isBlank()) return 0;
        return Integer.parseInt(value.toString());
    }

    public record Metrics(String documentId, String strategy, String contentType, int chunkCount,
                          int averageTokenCount, int maxTokenCount, int overlapTokenCount) {
    }
}
