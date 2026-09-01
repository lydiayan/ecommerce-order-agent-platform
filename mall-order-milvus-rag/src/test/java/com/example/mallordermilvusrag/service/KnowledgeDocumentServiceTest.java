package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.ChunkPreviewResponse;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentSummary;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;
import com.example.mallordermilvusrag.dto.KnowledgeImportStatus;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunk;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.splitter.token.TokenCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest {

    @Mock
    private DocumentMetadataRegistry metadataRegistry;
    @Mock
    private DocumentService documentService;
    @Mock
    private RagService ragService;
    @Mock
    private TokenCounter tokenCounter;
    @Mock
    private KnowledgeDocumentStore documentStore;

    private RagDocumentProperties documentProperties;
    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp() {
        documentProperties = new RagDocumentProperties();
        RagSplitterProperties splitterProperties = new RagSplitterProperties();
        KnowledgeChunkMetrics chunkMetrics = new KnowledgeChunkMetrics(splitterProperties);
        service = new KnowledgeDocumentService(documentProperties, splitterProperties, metadataRegistry,
                documentService, ragService, tokenCounter, new PdfUploadPolicy(), documentStore, chunkMetrics);
    }

    @Test
    void pdfPreviewUsesRealSplitterWithoutWritingMilvus() throws Exception {
        DocumentMetadata metadata = metadata("rules.pdf", "2026-08-27");
        MockMultipartFile file = new MockMultipartFile(
                "file", "rules.pdf", "application/pdf", new byte[]{1, 2, 3});
        List<RagChunk> chunks = List.of(
                chunk("chunk-0", 0, 120, 0, 240),
                chunk("chunk-1", 1, 80, 200, 360));
        when(metadataRegistry.resolve("rules.pdf", null, null, "5.1")).thenReturn(metadata);
        when(documentService.parsePdfChunks(file.getBytes(), "rules.pdf", metadata,
                "doc-rules", RagSplitStrategy.CONTENT_TYPE_AWARE)).thenReturn(chunks);

        ChunkPreviewResponse response = service.previewPdf(file, null, null, "5.1",
                "doc-rules", RagSplitStrategy.CONTENT_TYPE_AWARE);

        assertEquals(2, response.chunkCount());
        assertEquals(100, response.averageTokenCount());
        assertEquals(120, response.maxTokenCount());
        assertEquals(40, response.overlapTokens());
        assertEquals("normalized_text", response.chunks().get(0).offsetBasis());
        assertFalse(response.chunks().get(0).splitDegraded());
        verifyNoInteractions(ragService);
    }

    @Test
    void catalogSelectsLatestStoredDocumentVersionAndIncludesUnconfiguredSource() {
        RagDocumentProperties.CatalogEntry entry = new RagDocumentProperties.CatalogEntry();
        entry.setFilename("rules.pdf");
        entry.setDepartment("Operations");
        entry.setRole("public");
        entry.setVersion("5.1");
        documentProperties.setCatalog(List.of(entry));
        DocumentMetadata configured = metadata("rules.pdf", "2026-08-27");
        when(metadataRegistry.resolve("rules.pdf")).thenReturn(configured);
        when(ragService.listAllChunks()).thenReturn(List.of(
                hit("old-0", "doc-old", "rules.pdf", "2026-01-01", 0),
                hit("new-0", "doc-new", "rules.pdf", "2026-08-27", 0),
                hit("new-1", "doc-new", "rules.pdf", "2026-08-27", 1),
                hit("extra-0", "doc-extra", "extra.pdf", "2026-08-26", 0)));

        List<KnowledgeDocumentSummary> summaries = service.catalog();

        assertEquals(2, summaries.size());
        assertEquals("doc-new", summaries.get(0).documentId());
        assertEquals(2, summaries.get(0).chunkCount());
        assertEquals("extra.pdf", summaries.get(1).filename());
        assertEquals("doc-extra", summaries.get(1).documentId());
    }

    @Test
    void catalogPrefersMysqlSummaryOverMilvusDerivedMetadata() {
        LocalDateTime importedAt = LocalDateTime.of(2026, 8, 27, 20, 30);
        KnowledgeDocumentRecord stored = new KnowledgeDocumentRecord(
                "rules.pdf", metadata("rules.pdf", "2026-08-27"), "doc-mysql",
                RagSplitStrategy.PARENT_CHILD.name(), RagContentType.PDF.name(), 9,
                180, 320, 40, 2048, "abc123", KnowledgeImportStatus.READY,
                null, importedAt, importedAt);
        when(documentStore.findAll()).thenReturn(List.of(stored));
        when(ragService.listAllChunks()).thenReturn(List.of(
                hit("milvus-0", "doc-old", "rules.pdf", "2026-01-01", 0)));

        List<KnowledgeDocumentSummary> summaries = service.catalog();

        assertEquals(1, summaries.size());
        assertEquals("doc-mysql", summaries.get(0).documentId());
        assertEquals(9, summaries.get(0).chunkCount());
        assertEquals(180, summaries.get(0).averageTokenCount());
        assertEquals("READY", summaries.get(0).importStatus());
        assertEquals(importedAt, summaries.get(0).importedAt());
    }

    private static RagChunk chunk(String id, int index, int tokenCount, int start, int end) {
        return new RagChunk("content-" + id, "doc-rules", id, null, ChunkLevel.STANDALONE,
                index, 2, RagSplitStrategy.CONTENT_TYPE_AWARE, RagContentType.PDF, "Rules > Refund",
                start, end, tokenCount, Map.of(RagChunkMetadata.OFFSET_BASIS, "normalized_text"));
    }

    private static SearchResponse.SearchHit hit(String id, String documentId, String source,
                                                String createTime, int index) {
        SearchResponse.SearchHit hit = new SearchResponse.SearchHit(
                id, "stored " + id, 0, metadata(source, createTime));
        hit.setDocumentId(documentId);
        hit.setChunkLevel(ChunkLevel.STANDALONE.name());
        hit.setChunkIndex(index);
        hit.setTotalChunks(1);
        hit.setStrategy(RagSplitStrategy.CONTENT_TYPE_AWARE.name());
        hit.setContentType(RagContentType.PDF.name());
        return hit;
    }

    private static DocumentMetadata metadata(String source, String createTime) {
        return new DocumentMetadata(source, "Operations", "public", "5.1", createTime);
    }
}
