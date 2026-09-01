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

    /**
     * 对单文本 embedding 调用自动创建子 Span，并记录输入长度和向量维度。
     * 没有活动 Trace 时直接委托底层模型。
     *
     * @param text 待向量化文本
     * @return embedding 向量
     */
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

    /**
     * 使用文档正文执行受追踪的单文本 embedding。
     *
     * @param document 待向量化文档
     * @return embedding 向量
     */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /**
     * 直接委托完整 embedding 请求；批量调用不额外创建单文本 Span。
     *
     * @param request embedding 请求
     * @return 模型响应
     */
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
