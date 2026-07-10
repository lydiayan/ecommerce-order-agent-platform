package com.example.mallordermilvusrag.tracing;

import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Decorator around {@link EmbeddingModel} that automatically creates
 * {@code embed} child spans when called within an active RAG trace.
 *
 * <p>Batch methods delegate directly — only single-text {@link #embed(String)}
 * is traced, which is the hot path for search queries.
 */
public class RagTracingEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(RagTracingEmbeddingModel.class);

    private final EmbeddingModel delegate;
    private final RagTraceService ragTraceService;

    public RagTracingEmbeddingModel(EmbeddingModel delegate, RagTraceService ragTraceService) {
        this.delegate = delegate;
        this.ragTraceService = ragTraceService;
    }

    @Override
    public float[] embed(String text) {
        if (!ragTraceService.isEnabled() || ragTraceService.currentTraceId() == null) {
            return delegate.embed(text);
        }

        RagTraceScope span = ragTraceService.childSpan(RagTraceOperations.EMBED);
        try {
            float[] result = delegate.embed(text);
            span.attribute("dimension", result != null ? result.length : 0);
            span.attribute("queryLength", text != null ? text.length() : 0);
            return result;
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.close();
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return delegate.embed(texts);
    }

    @Override
    public List<float[]> embed(List<Document> documents,
                               org.springframework.ai.embedding.EmbeddingOptions options,
                               org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        return delegate.embed(documents, options, batchingStrategy);
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> inputs) {
        return delegate.embedForResponse(inputs);
    }
}
