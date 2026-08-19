package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BasicDocumentSplitterTest {

    @Test
    void fixedSizeEnforcesEstimatedTokenLimitAndStableIds() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                12, SplitterTestSupport.deterministicEmbedding());
        String text = "退款规则适用于普通商品。".repeat(20);
        RagSplitRequest request = SplitterTestSupport.request(text, RagSplitStrategy.FIXED_SIZE,
                RagContentType.PLAIN_TEXT);

        List<RagChunk> first = components.fixed().split(request);
        List<RagChunk> second = components.fixed().split(request);

        assertTrue(first.size() > 1);
        assertTrue(first.stream().allMatch(chunk -> chunk.tokenCount() <= 12));
        assertEquals(first.stream().map(RagChunk::chunkId).toList(),
                second.stream().map(RagChunk::chunkId).toList());
        assertTrue(first.stream().allMatch(chunk -> chunk.documentId().startsWith("doc_")));
    }

    @Test
    void slidingWindowCarriesSuffixIntoNextChunk() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                10, SplitterTestSupport.deterministicEmbedding());
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron";

        List<RagChunk> chunks = components.sliding().split(SplitterTestSupport.request(text,
                RagSplitStrategy.SLIDING_WINDOW, RagContentType.PLAIN_TEXT));

        assertTrue(chunks.size() >= 2);
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        assertTrue(first.substring(Math.max(0, first.length() - 8)).chars()
                .anyMatch(c -> second.indexOf(c) >= 0), "expected overlapping content");
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 10));
    }

    @Test
    void recursiveSplitterPrefersSentenceBoundary() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                16, SplitterTestSupport.deterministicEmbedding());
        String text = "第一句描述退款条件。第二句解释商品状态。第三句说明审核流程。第四句说明到账时间。";

        List<RagChunk> chunks = components.recursive().split(SplitterTestSupport.request(text,
                RagSplitStrategy.RECURSIVE, RagContentType.PLAIN_TEXT));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 16));
        assertTrue(chunks.get(0).content().endsWith("。"), chunks.get(0).content());
    }
}
