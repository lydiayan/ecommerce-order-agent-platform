package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentImportResult;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;
import com.example.mallordermilvusrag.dto.KnowledgeImportStatus;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportService.class);

    private final RagDocumentProperties properties;
    private final DocumentMetadataRegistry metadataRegistry;
    private final DocumentService documentService;
    private final RagService ragService;
    private final PdfUploadPolicy pdfUploadPolicy;
    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeChunkMetrics chunkMetrics;
    private final ConcurrentMap<String, ReentrantLock> importLocks = new ConcurrentHashMap<>();

    public DocumentImportService(RagDocumentProperties properties,
                                 DocumentMetadataRegistry metadataRegistry,
                                 DocumentService documentService,
                                 RagService ragService,
                                 PdfUploadPolicy pdfUploadPolicy,
                                 KnowledgeDocumentStore documentStore,
                                 KnowledgeChunkMetrics chunkMetrics) {
        this.properties = properties;
        this.metadataRegistry = metadataRegistry;
        this.documentService = documentService;
        this.ragService = ragService;
        this.pdfUploadPolicy = pdfUploadPolicy;
        this.documentStore = documentStore;
        this.chunkMetrics = chunkMetrics;
    }

    public DocumentImportResult importPdf(MultipartFile file,
                                          String departmentOverride,
                                          String roleOverride,
                                          String versionOverride) throws IOException {
        return importPdf(file, departmentOverride, roleOverride, versionOverride, null, null);
    }

    public DocumentImportResult importPdf(MultipartFile file,
                                          String departmentOverride,
                                          String roleOverride,
                                          String versionOverride,
                                          String documentId,
                                          RagSplitStrategy strategy) throws IOException {
        String filename = pdfUploadPolicy.validateAndResolveFilename(file);
        DocumentMetadata metadata = metadataRegistry.resolve(filename, departmentOverride, roleOverride, versionOverride);
        return importPdfBytes(file.getBytes(), filename, metadata, documentId, strategy);
    }

    public List<DocumentImportResult> importPdfs(List<MultipartFile> files) throws IOException {
        List<DocumentImportResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(importPdf(file, null, null, null));
        }
        return results;
    }

    /**
     * 从 classpath 配置的 data 目录批量导入 PDF（适合 8 份内置知识库文档一键入库）。
     */
    public List<DocumentImportResult> importFromClasspathDataDir() throws IOException {
        String pattern = "classpath:" + normalizeDir(properties.getDataDir()) + "/*.pdf";
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        Arrays.sort(resources, Comparator.comparing(r -> r.getFilename() != null ? r.getFilename() : ""));

        if (resources.length == 0) {
            throw new IllegalStateException("No PDF found under " + pattern);
        }

        List<DocumentImportResult> results = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename() != null ? resource.getFilename() : "unknown.pdf";
            DocumentMetadata metadata = metadataRegistry.resolve(filename);
            results.add(importPdfBytes(resource.getInputStream().readAllBytes(), filename, metadata, null, null));
        }
        log.info("Imported {} PDF(s) from {}", results.size(), pattern);
        return results;
    }

    private DocumentImportResult importPdfBytes(byte[] pdfBytes, String filename, DocumentMetadata metadata,
                                                String documentId, RagSplitStrategy strategy)
            throws IOException {
        ReentrantLock lock = importLocks.computeIfAbsent(filename, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new IllegalArgumentException("An import is already running for " + filename);
        }
        try {
            List<Document> documents = documentService.parsePdf(
                    pdfBytes, filename, metadata, documentId, strategy);
            KnowledgeChunkMetrics.Metrics metrics = chunkMetrics.fromDocuments(documents);
            KnowledgeDocumentRecord importing = new KnowledgeDocumentRecord(
                    filename, metadata, metrics.documentId(), metrics.strategy(), metrics.contentType(),
                    metrics.chunkCount(), metrics.averageTokenCount(), metrics.maxTokenCount(),
                    metrics.overlapTokenCount(), pdfBytes.length, sha256(pdfBytes),
                    KnowledgeImportStatus.IMPORTING, null, null, null);
            documentStore.saveImporting(importing);
            try {
                List<String> ids = ragService.addProcessedDocuments(documents);
                KnowledgeDocumentRecord ready = importing.withStatus(
                        KnowledgeImportStatus.READY, null, LocalDateTime.now());
                documentStore.saveReady(ready);
                return new DocumentImportResult(filename, metadata, ids.size(), ids);
            } catch (RuntimeException failure) {
                markFailed(filename, failure);
                throw failure;
            }
        } finally {
            lock.unlock();
            importLocks.remove(filename, lock);
        }
    }

    private void markFailed(String filename, RuntimeException failure) {
        String message = failure.getClass().getSimpleName()
                + (failure.getMessage() != null ? ": " + failure.getMessage() : "");
        try {
            documentStore.saveFailed(filename, message);
        } catch (RuntimeException persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
            log.error("Failed to record knowledge import failure for {}", filename, persistenceFailure);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return "data";
        }
        return dir.startsWith("/") ? dir.substring(1) : dir.replaceAll("/$", "");
    }
}
