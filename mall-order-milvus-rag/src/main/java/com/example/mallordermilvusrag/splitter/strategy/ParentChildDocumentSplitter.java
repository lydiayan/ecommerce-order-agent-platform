package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.core.AbstractRagDocumentSplitter;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 父子切分：父块提供完整上下文，子块提供更细粒度的向量召回入口。
 *
 * <p>查询时通常用子块匹配问题，再通过 {@code parentId} 聚合并返回父块内容。</p>
 */
@Component
public class ParentChildDocumentSplitter extends AbstractRagDocumentSplitter {

    private final ContentTypeDocumentSplitter contentTypeSplitter;
    private final RagSplitterProperties.ParentChildProperties parentChildProperties;

    public ParentChildDocumentSplitter(RagSplitterProperties properties, TokenCounter tokenCounter,
                                       ContentTypeDocumentSplitter contentTypeSplitter) {
        super(properties, tokenCounter);
        this.contentTypeSplitter = contentTypeSplitter;
        this.parentChildProperties = properties.getParentChild();
    }

    @Override
    public RagSplitStrategy strategy() {
        return RagSplitStrategy.PARENT_CHILD;
    }

    @Override
    protected List<ChunkDraft> splitDrafts(RagSplitRequest request) {
        // 父块不重叠，避免返回大量重复上下文；子块保留重叠，提升召回边界附近信息的概率。
        SplitOptions parentOptions = new SplitOptions(parentChildProperties.getParentTokens(), 0,
                Math.max(1, parentChildProperties.getParentTokens() / 4));
        SplitOptions childOptions = new SplitOptions(parentChildProperties.getChildTokens(),
                parentChildProperties.getChildOverlapTokens(),
                Math.max(1, parentChildProperties.getChildTokens() / 4));

        List<ChunkDraft> parents = contentTypeSplitter.splitForOptions(request, parentOptions);
        List<ChunkDraft> result = new ArrayList<>();
        for (int i = 0; i < parents.size(); i++) {
            ChunkDraft parent = parents.get(i);
            // groupKey 是切分阶段的临时关联键，最终由抽象基类转换成稳定 parentId。
            String groupKey = "parent-" + i;
            result.add(parent.withLevel(ChunkLevel.PARENT, groupKey));
            // HTML 父块已经被规范化成 Markdown 风格文本，子切分不能再次按 HTML 解析。
            RagContentType childContentType = request.contentType() == RagContentType.HTML
                    ? RagContentType.MARKDOWN : request.contentType();
            RagSplitRequest childRequest = new RagSplitRequest(parent.content(), request.metadata(),
                    request.documentId(), request.strategy(), childContentType);
            List<ChunkDraft> children = contentTypeSplitter.splitForOptions(childRequest, childOptions);
            for (ChunkDraft child : children) {
                // 子块 offset 是相对父块的局部位置，写出前要还原成文档级位置。
                java.util.Map<String, Object> childMetadata = new java.util.LinkedHashMap<>(parent.metadata());
                childMetadata.putAll(child.metadata());
                result.add(new ChunkDraft(child.content(), parent.startOffset() + child.startOffset(),
                        parent.startOffset() + child.endOffset(),
                        child.titlePath().isBlank() ? parent.titlePath() : child.titlePath(),
                        ChunkLevel.CHILD, groupKey, childMetadata));
            }
        }
        return result;
    }
}
