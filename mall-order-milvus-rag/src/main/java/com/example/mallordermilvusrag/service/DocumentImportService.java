package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentImportResult;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class DocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportService.class);

    private final RagDocumentProperties properties;
    private final DocumentMetadataRegistry metadataRegistry;
    private final DocumentService documentService;
    private final RagService ragService;

    public DocumentImportService(RagDocumentProperties properties,
                                 DocumentMetadataRegistry metadataRegistry,
                                 DocumentService documentService,
                                 RagService ragService) {
        this.properties = properties;
        this.metadataRegistry = metadataRegistry;
        this.documentService = documentService;
        this.ragService = ragService;
    }

    public DocumentImportResult importPdf(MultipartFile file,
                                          String departmentOverride,
                                          String roleOverride,
                                          String versionOverride) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.pdf";
        DocumentMetadata metadata = metadataRegistry.resolve(filename, departmentOverride, roleOverride, versionOverride);
        return importPdfBytes(file.getBytes(), filename, metadata);
    }

    public List<DocumentImportResult> importPdfs(List<MultipartFile> files) throws IOException {
        List<DocumentImportResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(importPdf(file, null, null, null));
        }
        return results;
    }

    /**
     * 从 classpath 配置的 data 目录批量导入 PDF（适合 7 份内置知识库文档一键入库）。
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
            results.add(importPdfBytes(resource.getInputStream().readAllBytes(), filename, metadata));
        }
        log.info("Imported {} PDF(s) from {}", results.size(), pattern);
        return results;
    }

    private DocumentImportResult importPdfBytes(byte[] pdfBytes, String filename, DocumentMetadata metadata)
            throws IOException {
        List<Document> documents = documentService.parsePdf(pdfBytes, filename, metadata);
        List<String> ids = ragService.addProcessedDocuments(documents);
        return new DocumentImportResult(filename, metadata, ids.size(), ids);
    }

    private static String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return "data";
        }
        return dir.startsWith("/") ? dir.substring(1) : dir.replaceAll("/$", "");
    }
}
