package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 递归切分：在 Token 硬上限内依次尝试段落、换行、句子等分隔符，尽量保留自然语义边界。
 */
@Component
public class RecursiveDocumentSplitter extends AbstractRagDocumentSplitter {

    private final RagSplitterProperties.RecursiveProperties recursiveProperties;

    public RecursiveDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter) {
        super(properties, tokenCounter);
        this.recursiveProperties = properties.getRecursive();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.RECURSIVE;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        SplitOptions options = new SplitOptions(recursiveProperties.getMaxTokens(),
                recursiveProperties.getOverlapTokens(), recursiveProperties.getMinTokens());
        return splitRange(request.text(), 0, options);
    }

    List<ChunkDraft> splitRange(String text, int baseOffset, SplitOptions options) {
        // baseOffset 允许其他策略切分局部文本后，将局部位置还原成整篇文档的位置。
        return recursiveWindows(text, baseOffset, options.maxTokens(), options.overlapTokens(),
                options.minTokens(), recursiveProperties.getSeparators());
    }
}
