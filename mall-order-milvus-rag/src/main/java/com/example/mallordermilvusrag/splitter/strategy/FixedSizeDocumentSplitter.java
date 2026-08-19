package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定大小切分：连续取不超过配置 Token 上限的文本，块之间没有重叠。
 *
 * <p>实现简单、吞吐稳定，适合结构不明显且不需要跨块上下文的文本。</p>
 */
@Component
public class FixedSizeDocumentSplitter extends AbstractRagDocumentSplitter {

    private final RagSplitterProperties.FixedSizeProperties fixedSizeProperties;

    public FixedSizeDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter) {
        super(properties, tokenCounter);
        this.fixedSizeProperties = properties.getFixedSize();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.FIXED_SIZE;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        return windows(request.text(), 0, fixedSizeProperties.getMaxTokens(), 0);
    }
}
