package com.example.mallordermilvusrag.controller;

import com.example.mallordermilvusrag.dto.*;
import com.example.mallordermilvusrag.service.DocumentImportService;
import com.example.mallordermilvusrag.service.DocumentService;
import com.example.mallordermilvusrag.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Milvus RAG 向量检索 REST API
 */
@RestController
@RequestMapping("/vector/milvus")
public class VectorMilvusController {

    private static final Logger log = LoggerFactory.getLogger(VectorMilvusController.class);

    private final RagService ragService;
    private final DocumentService documentService;
    private final DocumentImportService documentImportService;

    public VectorMilvusController(RagService ragService,
                                  DocumentService documentService,
                                  DocumentImportService documentImportService) {
        this.ragService = ragService;
        this.documentService = documentService;
        this.documentImportService = documentImportService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Milvus RAG service is running");
    }

    @PostMapping("/documents")
    public ApiResponse<List<String>> addDocument(@RequestBody AddDocumentRequest request) {
        log.info("POST /vector/milvus/documents - adding document");
        List<String> ids = ragService.addDocument(request.getText(), request.getMetadata());
        return ApiResponse.success("Document added successfully, split into " + ids.size() + " chunks", ids);
    }

    @PostMapping("/documents/batch")
    public ApiResponse<List<String>> batchAddDocuments(@RequestBody BatchAddDocumentsRequest request) {
        log.info("POST /vector/milvus/documents/batch - adding {} documents", request.getDocuments().size());

        List<DocumentService.DocumentInput> inputs = request.getDocuments().stream()
                .map(doc -> new DocumentService.DocumentInput(doc.getText(), doc.getMetadata()))
                .collect(Collectors.toList());

        List<String> ids = ragService.addDocuments(inputs);
        return ApiResponse.success("Batch documents added, total " + ids.size() + " chunks", ids);
    }

    /**
     * 上传单个 PDF。metadata 按文件名从 application.yml 的 rag.catalog 自动解析；
     * 仅需在 catalog 未覆盖时传 department/role/version 覆盖。
     */
    @PostMapping(value = "/documents/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentImportResult> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "version", required = false) String version) throws IOException {

        log.info("POST /vector/milvus/documents/pdf - uploading file: {}", file.getOriginalFilename());
        DocumentImportResult result = documentImportService.importPdf(file, department, role, version);
        return ApiResponse.success(
                "PDF processed: " + result.getFilename() + ", " + result.getChunkCount() + " chunks",
                result);
    }

    /**
     * 批量上传 PDF，每个文件按 catalog 文件名自动匹配 metadata。
     */
    @PostMapping(value = "/documents/pdf/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<DocumentImportResult>> uploadPdfBatch(
            @RequestParam("files") MultipartFile[] files) throws IOException {

        log.info("POST /vector/milvus/documents/pdf/batch - uploading {} file(s)", files.length);
        List<DocumentImportResult> results = documentImportService.importPdfs(Arrays.asList(files));
        int totalChunks = results.stream().mapToInt(DocumentImportResult::getChunkCount).sum();
        return ApiResponse.success("Batch PDF import done, total " + totalChunks + " chunks", results);
    }

    /**
     * 一键导入 classpath:data 目录下全部 PDF（内置 7 份知识库文档）。
     */
    @PostMapping("/documents/import-local")
    public ApiResponse<List<DocumentImportResult>> importLocalPdfs() throws IOException {
        log.info("POST /vector/milvus/documents/import-local");
        List<DocumentImportResult> results = documentImportService.importFromClasspathDataDir();
        int totalChunks = results.stream().mapToInt(DocumentImportResult::getChunkCount).sum();
        return ApiResponse.success(
                "Imported " + results.size() + " PDF(s), total " + totalChunks + " chunks",
                results);
    }

    @PostMapping("/search")
    public ApiResponse<SearchResponse> search(@RequestBody SearchRequest request) {
        log.info("POST /vector/milvus/search - query='{}', topK={}, roleFilter='{}'",
                request.getQuery(), request.getTopK(), request.getRoleFilter());

        String filterExpr = RagService.buildFilterExpression(
                request.getSourceFilter(),
                request.getDepartmentFilter(),
                request.getRoleFilter(),
                request.getVersionFilter()
        );

        double threshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold() : 0.0;

        SearchResponse response = ragService.searchWithFilter(
                request.getQuery(), request.getTopK(), threshold, filterExpr);

        return ApiResponse.success(response);
    }

    @GetMapping("/search")
    public ApiResponse<SearchResponse> searchGet(
            @RequestParam("q") String q,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        log.info("GET /vector/milvus/search - query='{}', topK={}", q, topK);
        SearchResponse response = ragService.search(q, topK);
        return ApiResponse.success(response);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(ragService.stats());
    }
}
