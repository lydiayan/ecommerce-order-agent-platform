package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.token.JTokkitTokenCounter;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import com.example.mallordermilvusrag.splitter.registry.DocumentSplitterRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SplitterTestSupport {

    private SplitterTestSupport() {
    }

    static RagSplitterProperties properties(int chunkSize) {
        RagSplitterProperties properties = new RagSplitterProperties();
        properties.getFixedSize().setMaxTokens(chunkSize);
        properties.getSlidingWindow().setMaxTokens(chunkSize);
        properties.getSlidingWindow().setOverlapTokens(Math.max(1, chunkSize / 4));
        properties.getRecursive().setMinTokens(Math.max(1, chunkSize / 4));
        properties.getRecursive().setMaxTokens(chunkSize);
        properties.getRecursive().setOverlapTokens(Math.max(1, chunkSize / 5));
        properties.getSemantic().setMinTokens(1);
        properties.getSemantic().setTargetTokens(Math.max(2, chunkSize / 2));
        properties.getSemantic().setMaxTokens(chunkSize);
        properties.getSemantic().setBoundaryPercentile(0.5);
        properties.getParentChild().setParentTokens(chunkSize * 2);
        properties.getParentChild().setChildTokens(chunkSize);
        properties.getParentChild().setChildOverlapTokens(Math.max(1, chunkSize / 5));
        properties.afterPropertiesSet();
        return properties;
    }

    static RagSplitRequest request(String text, RagSplitStrategy strategy, RagContentType type) {
        return new RagSplitRequest(text, Map.of("source", "test.md", "version", "1"),
                null, strategy, type);
    }

    static Components components(int chunkSize, EmbeddingModel embeddingModel) {
        RagSplitterProperties properties = properties(chunkSize);
        TokenCounter counter = new JTokkitTokenCounter();
        RecursiveDocumentSplitter recursive = new RecursiveDocumentSplitter(properties, counter);
        StructureAwareDocumentSplitter structure = new StructureAwareDocumentSplitter(properties, counter, recursive);
        ContentTypeDocumentSplitter content = new ContentTypeDocumentSplitter(properties, counter, recursive, structure);
        ParentChildDocumentSplitter parentChild = new ParentChildDocumentSplitter(properties, counter, content);
        FixedSizeDocumentSplitter fixed = new FixedSizeDocumentSplitter(properties, counter);
        SlidingWindowDocumentSplitter sliding = new SlidingWindowDocumentSplitter(properties, counter);
        SemanticDocumentSplitter semantic = new SemanticDocumentSplitter(properties, counter, embeddingModel, recursive);
        DocumentSplitterRegistry registry = new DocumentSplitterRegistry(
                List.of(fixed, sliding, recursive, structure, semantic, parentChild, content), content, properties);
        return new Components(properties, counter, fixed, sliding, recursive, structure,
                semantic, parentChild, content, registry);
    }

    static EmbeddingModel deterministicEmbedding() {
        return new EmbeddingModel() {
            @Override
            public float[] embed(Document document) {
                return vector(document.getText());
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(
                    org.springframework.ai.embedding.EmbeddingRequest request) {
                List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();
                for (int i = 0; i < request.getInstructions().size(); i++) {
                    embeddings.add(new org.springframework.ai.embedding.Embedding(
                            vector(request.getInstructions().get(i)), i));
                }
                return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
            }

            private float[] vector(String value) {
                String lower = value.toLowerCase();
                return lower.contains("refund") || lower.contains("退款")
                        ? new float[]{1, 0, 0}
                        : lower.contains("shipping") || lower.contains("物流")
                        ? new float[]{0, 1, 0}
                        : new float[]{0, 0, 1};
            }
        };
    }

    record Components(RagSplitterProperties properties, TokenCounter counter,
                      FixedSizeDocumentSplitter fixed, SlidingWindowDocumentSplitter sliding,
                      RecursiveDocumentSplitter recursive, StructureAwareDocumentSplitter structure,
                      SemanticDocumentSplitter semantic, ParentChildDocumentSplitter parentChild,
                      ContentTypeDocumentSplitter content, DocumentSplitterRegistry registry) {
    }
}
