package com.example.mallordermilvusrag.splitter.registry;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.strategy.ContentTypeDocumentSplitter;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 切分策略注册表，也是业务层调用切分功能的统一入口。
 */
@Component
public class DocumentSplitterRegistry {

    private final Map<RagSplitStrategy, AbstractRagDocumentSplitter> splitters;
    private final ContentTypeDocumentSplitter contentTypeSplitter;
    private final RagSplitterProperties properties;

    public DocumentSplitterRegistry(List<AbstractRagDocumentSplitter> splitters,
                                    ContentTypeDocumentSplitter contentTypeSplitter,
                                    RagSplitterProperties properties) {
        this.contentTypeSplitter = contentTypeSplitter;
        this.properties = properties;
        Map<RagSplitStrategy, AbstractRagDocumentSplitter> indexed = new EnumMap<>(RagSplitStrategy.class);
        // 启动时尽早发现重复实现，避免运行时静默覆盖某个策略。
        for (AbstractRagDocumentSplitter splitter : splitters) {
            if (indexed.put(splitter.strategy(), splitter) != null) {
                throw new IllegalStateException("Duplicate splitter for " + splitter.strategy());
            }
        }
        // 新增枚举却忘记注册实现时直接启动失败，保证每个策略都可用。
        for (RagSplitStrategy strategy : RagSplitStrategy.values()) {
            if (!indexed.containsKey(strategy)) {
                throw new IllegalStateException("Missing splitter for " + strategy);
            }
        }
        this.splitters = Map.copyOf(indexed);
    }

    public List<RagChunk> split(RagSplitRequest request) {
        // 先补默认策略并识别内容类型，再把完全归一化的请求交给目标实现。
        RagSplitStrategy strategy = request.strategy() == null
                ? properties.getStrategy() : request.strategy();
        RagContentType contentType = contentTypeSplitter.detect(request);
        RagSplitRequest normalized = new RagSplitRequest(request.text(), request.metadata(),
                request.documentId(), strategy, contentType);
        return splitters.get(strategy).split(normalized);
    }
}
