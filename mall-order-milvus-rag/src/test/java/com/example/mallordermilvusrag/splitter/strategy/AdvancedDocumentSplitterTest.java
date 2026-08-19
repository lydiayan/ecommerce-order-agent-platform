package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedDocumentSplitterTest {

    @Test
    void structureSplitterKeepsHeadingPath() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                20, SplitterTestSupport.deterministicEmbedding());
        String text = "# 退款规则\n\n## 普通商品\n签收后七天内可以申请退款。商品必须完好。\n\n## 生鲜商品\n腐坏后应在二十四小时内申请。";

        List<RagChunk> chunks = components.structure().split(SplitterTestSupport.request(text,
                RagSplitStrategy.STRUCTURE_AWARE, RagContentType.MARKDOWN));

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.titlePath().contains("退款规则 > 普通商品")));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.titlePath().contains("退款规则 > 生鲜商品")));
    }

    @Test
    void semanticSplitterFindsTopicBoundary() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                40, SplitterTestSupport.deterministicEmbedding());
        String text = "退款申请需要订单号。退款审核需要商品照片。物流发货后生成运单。物流异常需要联系客服。";

        List<RagChunk> chunks = components.semantic().split(SplitterTestSupport.request(text,
                RagSplitStrategy.SEMANTIC, RagContentType.PLAIN_TEXT));

        assertTrue(chunks.size() >= 2, chunks.toString());
        assertTrue(chunks.get(0).content().contains("退款"));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("物流")));
    }

    @Test
    void semanticFailureFallsBackAndMarksMetadata() {
        EmbeddingModel failing = new EmbeddingModel() {
            @Override
            public float[] embed(Document document) {
                throw new IllegalStateException("offline");
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(
                    org.springframework.ai.embedding.EmbeddingRequest request) {
                throw new IllegalStateException("offline");
            }
        };
        SplitterTestSupport.Components components = SplitterTestSupport.components(20, failing);

        List<RagChunk> chunks = components.semantic().split(SplitterTestSupport.request(
                "退款申请需要订单号。退款审核需要照片。物流异常需要联系客服。",
                RagSplitStrategy.SEMANTIC, RagContentType.PLAIN_TEXT));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> Boolean.TRUE.equals(
                chunk.metadata().get(RagChunkMetadata.SPLIT_DEGRADED))));
        assertEquals("embedding_failure",
                chunks.get(0).metadata().get(RagChunkMetadata.SPLIT_DEGRADED_REASON));
    }

    @Test
    void parentChildCreatesResolvableRelationships() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                16, SplitterTestSupport.deterministicEmbedding());
        String text = "退款规则适用于普通商品。商品应保持完好。审核通过后创建退货单。退款将在三个工作日到账。";

        List<RagChunk> chunks = components.parentChild().split(SplitterTestSupport.request(text,
                RagSplitStrategy.PARENT_CHILD, RagContentType.PLAIN_TEXT));

        List<RagChunk> parents = chunks.stream().filter(chunk -> chunk.chunkLevel() == ChunkLevel.PARENT).toList();
        List<RagChunk> children = chunks.stream().filter(chunk -> chunk.chunkLevel() == ChunkLevel.CHILD).toList();
        assertFalse(parents.isEmpty());
        assertFalse(children.isEmpty());
        assertTrue(children.stream().allMatch(child -> child.parentId() != null));
        assertTrue(children.stream().allMatch(child -> parents.stream()
                .anyMatch(parent -> parent.chunkId().equals(child.parentId()))));
        assertTrue(children.stream().allMatch(child -> child.tokenCount() <= 16));
    }
}
