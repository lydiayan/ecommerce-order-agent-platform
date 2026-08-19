package com.example.mallordermilvusrag.splitter.strategy;

import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitRequest;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentTypeDocumentSplitterTest {

    @Test
    void detectsSupportedTypesAndHonorsExplicitOverride() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                30, SplitterTestSupport.deterministicEmbedding());

        assertEquals(RagContentType.MARKDOWN, components.content().detect(
                SplitterTestSupport.request("# Title\nBody", null, null)));
        assertEquals(RagContentType.HTML, components.content().detect(
                SplitterTestSupport.request("<html><p>Body</p></html>", null, null)));
        assertEquals(RagContentType.FAQ, components.content().detect(
                SplitterTestSupport.request("Q: Refund?\nA: Yes.", null, null)));
        assertEquals(RagContentType.TABLE, components.content().detect(
                SplitterTestSupport.request("name | value\na | 1\nb | 2", null, null)));
        assertEquals(RagContentType.CODE, components.content().detect(
                SplitterTestSupport.request("```java\nclass A {}\n```", null, null)));
        assertEquals(RagContentType.PLAIN_TEXT, components.content().detect(
                SplitterTestSupport.request("plain prose", null, RagContentType.PLAIN_TEXT)));
    }

    @Test
    void faqPairsStayAtomicAndTablesRepeatHeader() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                16, SplitterTestSupport.deterministicEmbedding());

        List<RagChunk> faq = components.content().split(SplitterTestSupport.request(
                "Q: Can I refund?\nA: Yes.\nQ: How long?\nA: Three days.",
                RagSplitStrategy.CONTENT_TYPE_AWARE, RagContentType.FAQ));
        assertEquals(2, faq.size());
        assertTrue(faq.stream().allMatch(chunk -> chunk.content().contains("A:")));

        List<RagChunk> table = components.content().split(SplitterTestSupport.request(
                "item | policy\nphone | seven days\nfresh food | damaged only\nbook | seven days\nclothes | unused only",
                RagSplitStrategy.CONTENT_TYPE_AWARE, RagContentType.TABLE));
        assertTrue(table.size() >= 2);
        assertTrue(table.stream().allMatch(chunk -> chunk.content().startsWith("item | policy")));
    }

    @Test
    void registryAppliesGlobalDefaultAndPreservesRichMetadata() {
        SplitterTestSupport.Components components = SplitterTestSupport.components(
                20, SplitterTestSupport.deterministicEmbedding());
        RagSplitRequest request = new RagSplitRequest("# Rules\nRefunds are supported.",
                java.util.Map.of("source", "rules.md"), "business-doc-1", null, null);

        List<RagChunk> chunks = components.registry().split(request);

        assertFalse(chunks.isEmpty());
        assertEquals("business-doc-1", chunks.get(0).documentId());
        assertEquals(RagSplitStrategy.CONTENT_TYPE_AWARE, chunks.get(0).strategy());
        assertEquals(RagContentType.MARKDOWN, chunks.get(0).contentType());
        assertEquals("business-doc-1",
                chunks.get(0).toDocument().getMetadata().get(RagChunkMetadata.DOCUMENT_ID));
    }
}
