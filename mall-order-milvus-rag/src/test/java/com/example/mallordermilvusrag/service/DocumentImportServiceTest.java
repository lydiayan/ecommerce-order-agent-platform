package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentImportResult;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;
import com.example.mallordermilvusrag.dto.KnowledgeImportStatus;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.config.RagSplitterProperties;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.mock.web.MockMultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentImportServiceTest {

    @Mock
    private DocumentMetadataRegistry metadataRegistry;
    @Mock
    private DocumentService documentService;
    @Mock
    private RagService ragService;
    @Mock
    private KnowledgeDocumentStore documentStore;

    private DocumentImportService service;

    @BeforeEach
    void setUp() {
        RagSplitterProperties splitterProperties = new RagSplitterProperties();
        service = new DocumentImportService(new RagDocumentProperties(), metadataRegistry,
                documentService, ragService, new PdfUploadPolicy(), documentStore,
                new KnowledgeChunkMetrics(splitterProperties));
    }

    @Test
    void successfulImportPersistsMysqlSummaryAroundMilvusWrite() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "rules.pdf", "application/pdf", bytes);
        DocumentMetadata metadata = new DocumentMetadata(
                "rules.pdf", "Operations", "public", "5.2", "2026-08-27");
        List<Document> documents = List.of(document("chunk-0", 120), document("chunk-1", 80));
        when(metadataRegistry.resolve("rules.pdf", "Operations", "public", "5.2"))
                .thenReturn(metadata);
        when(documentService.parsePdf(bytes, "rules.pdf", metadata, "doc-rules",
                RagSplitStrategy.CONTENT_TYPE_AWARE)).thenReturn(documents);
        when(ragService.addProcessedDocuments(documents)).thenReturn(List.of("chunk-0", "chunk-1"));

        DocumentImportResult result = service.importPdf(file, "Operations", "public", "5.2",
                "doc-rules", RagSplitStrategy.CONTENT_TYPE_AWARE);

        assertEquals(2, result.getChunkCount());
        InOrder order = inOrder(documentStore, ragService);
        order.verify(documentStore).saveImporting(org.mockito.ArgumentMatchers.any());
        order.verify(ragService).addProcessedDocuments(documents);
        ArgumentCaptor<KnowledgeDocumentRecord> ready = ArgumentCaptor.forClass(KnowledgeDocumentRecord.class);
        order.verify(documentStore).saveReady(ready.capture());
        assertEquals(KnowledgeImportStatus.READY, ready.getValue().importStatus());
        assertEquals(100, ready.getValue().averageTokenCount());
        assertEquals(120, ready.getValue().maxTokenCount());
        assertEquals(40, ready.getValue().overlapTokenCount());
        assertEquals(bytes.length, ready.getValue().originalFileSize());
        assertEquals(64, ready.getValue().fileSha256().length());
        assertNotNull(ready.getValue().importedAt());
    }

    @Test
    void failedMilvusWriteMarksMysqlRecordFailed() throws Exception {
        byte[] bytes = {9, 8, 7};
        MockMultipartFile file = new MockMultipartFile("file", "rules.pdf", "application/pdf", bytes);
        DocumentMetadata metadata = new DocumentMetadata(
                "rules.pdf", "Operations", "public", "5.2", "2026-08-27");
        List<Document> documents = List.of(document("chunk-0", 90));
        when(metadataRegistry.resolve("rules.pdf", null, null, null)).thenReturn(metadata);
        when(documentService.parsePdf(bytes, "rules.pdf", metadata, null, null)).thenReturn(documents);
        when(ragService.addProcessedDocuments(documents)).thenThrow(new IllegalStateException("offline"));

        assertThrows(IllegalStateException.class,
                () -> service.importPdf(file, null, null, null));

        verify(documentStore).saveFailed(eq("rules.pdf"), contains("offline"));
    }

    private static Document document(String id, int tokenCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(RagChunkMetadata.DOCUMENT_ID, "doc-rules");
        metadata.put(RagChunkMetadata.CHUNK_ID, id);
        metadata.put(RagChunkMetadata.CHUNK_LEVEL, ChunkLevel.STANDALONE.name());
        metadata.put(RagChunkMetadata.STRATEGY, RagSplitStrategy.CONTENT_TYPE_AWARE.name());
        metadata.put(RagChunkMetadata.CONTENT_TYPE, RagContentType.PDF.name());
        metadata.put(RagChunkMetadata.TOKEN_COUNT, tokenCount);
        return new Document(id, "content " + id, metadata);
    }
}
