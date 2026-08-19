package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 滑动窗口切分：每个块保留上一块末尾的一部分 Token，降低语义被边界截断的概率。
 */
@Component
public class SlidingWindowDocumentSplitter extends AbstractRagDocumentSplitter {

    private final RagSplitterProperties.SlidingWindowProperties windowProperties;

    public SlidingWindowDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter) {
        super(properties, tokenCounter);
        this.windowProperties = properties.getSlidingWindow();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.SLIDING_WINDOW;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        // 与固定大小切分共用窗口算法，区别只在 overlapTokens 是否为 0。
        return windows(request.text(), 0, windowProperties.getMaxTokens(), windowProperties.getOverlapTokens());
    }
}
