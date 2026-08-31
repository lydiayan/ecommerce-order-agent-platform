package com.example.mallordermilvusrag.controller;

import com.example.mallordermilvusrag.dto.*;
import com.example.mallordermilvusrag.service.DocumentImportService;
import com.example.mallordermilvusrag.service.DocumentService;
import com.example.mallordermilvusrag.service.KnowledgeDocumentService;
import com.example.mallordermilvusrag.service.RagAskService;
import com.example.mallordermilvusrag.service.RagService;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
    private final RagAskService ragAskService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public VectorMilvusController(RagService ragService,
                                  DocumentService documentService,
                                  DocumentImportService documentImportService,
                                  RagAskService ragAskService,
                                  KnowledgeDocumentService knowledgeDocumentService) {
        this.ragService = ragService;
        this.documentService = documentService;
        this.documentImportService = documentImportService;
        this.ragAskService = ragAskService;
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Milvus RAG service is running");
    }

    @PostMapping("/documents")
    public ApiResponse<List<String>> addDocument(@RequestBody AddDocumentRequest request) {
        log.info("POST /vector/milvus/documents - adding document");
        List<String> ids = ragService.addDocument(request.getText(), request.getMetadata(),
                request.getDocumentId(), request.getStrategy(), request.getContentType());
        return ApiResponse.success("Document added successfully, split into " + ids.size() + " chunks", ids);
    }

    @GetMapping("/documents/catalog")
    public ApiResponse<List<KnowledgeDocumentSummary>> documentCatalog() {
        return ApiResponse.success(knowledgeDocumentService.catalog());
    }

    @GetMapping("/documents/chunks")
    public ApiResponse<ChunkPreviewResponse> documentChunks(@RequestParam("source") String source) {
        return ApiResponse.success(knowledgeDocumentService.storedDocument(source));
    }

    @PostMapping("/documents/preview")
    public ApiResponse<ChunkPreviewResponse> previewDocument(@RequestBody AddDocumentRequest request) {
        return ApiResponse.success(knowledgeDocumentService.previewText(request));
    }

    @PostMapping(value = "/documents/preview/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChunkPreviewResponse> previewPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "documentId", required = false) String documentId,
            @RequestParam(value = "strategy", required = false) RagSplitStrategy strategy) throws IOException {
        return ApiResponse.success(knowledgeDocumentService.previewPdf(
                file, department, role, version, documentId, strategy));
    }

    @PostMapping("/documents/batch")
    public ApiResponse<List<String>> batchAddDocuments(@RequestBody BatchAddDocumentsRequest request) {
        log.info("POST /vector/milvus/documents/batch - adding {} documents", request.getDocuments().size());

        List<DocumentService.DocumentInput> inputs = request.getDocuments().stream()
                .map(doc -> new DocumentService.DocumentInput(doc.getText(), doc.getMetadata(),
                        doc.getDocumentId(), doc.getStrategy(), doc.getContentType()))
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
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "documentId", required = false) String documentId,
            @RequestParam(value = "strategy", required = false) RagSplitStrategy strategy) throws IOException {

        log.info("POST /vector/milvus/documents/pdf - uploading file: {}", file.getOriginalFilename());
        DocumentImportResult result = documentImportService.importPdf(
                file, department, role, version, documentId, strategy);
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
     * 一键导入 classpath:data 目录下全部 PDF（内置 8 份知识库文档）。
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
        log.info("POST /vector/milvus/search - queryLength={}, topK={}, filtered={}, enableRerank={}",
                request.getQuery() != null ? request.getQuery().length() : 0,
                request.getTopK(), request.getRoleFilter() != null, request.getEnableRerank());

        SearchResponse response = ragService.search(request);

        return ApiResponse.success(response);
    }

    /**
     * RAG 问答：Milvus 召回 → qwen3-rerank → Qwen 生成回答。
     * <p>
     * 请求体与 {@link #search(SearchRequest)} 相同，{@code query} 为用户问题。
     */
    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody SearchRequest request) {
        log.info("POST /vector/milvus/ask - queryLength={}, filtered={}",
                request.getQuery() != null ? request.getQuery().length() : 0,
                request.getRoleFilter() != null);
        AskResponse response = ragAskService.ask(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/search")
    public ApiResponse<SearchResponse> searchGet(
            @RequestParam("q") String q,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        log.info("GET /vector/milvus/search - queryLength={}, topK={}", q.length(), topK);
        SearchResponse response = ragService.search(q, topK);
        return ApiResponse.success(response);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(ragService.stats());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }
}
