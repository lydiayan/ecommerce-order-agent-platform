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

    /**
     * 检查 Milvus RAG HTTP 服务是否已经启动并可接收请求。
     *
     * @return 固定的服务运行状态说明
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Milvus RAG service is running");
    }

    /**
     * 将一篇文本按指定策略切分、向量化并写入知识库。
     *
     * @param request 文本正文、元数据、文档编号、内容类型和切分策略
     * @return 本次写入生成的全部向量分块编号
     */
    @PostMapping("/documents")
    public ApiResponse<List<String>> addDocument(@RequestBody AddDocumentRequest request) {
        log.info("POST /vector/milvus/documents - adding document");
        List<String> ids = ragService.addDocument(request.getText(), request.getMetadata(),
                request.getDocumentId(), request.getStrategy(), request.getContentType());
        return ApiResponse.success("Document added successfully, split into " + ids.size() + " chunks", ids);
    }

    /**
     * 查询知识库文档目录及其导入状态和分块统计。
     *
     * @return 已登记知识文档的摘要列表
     */
    @GetMapping("/documents/catalog")
    public ApiResponse<List<KnowledgeDocumentSummary>> documentCatalog() {
        return ApiResponse.success(knowledgeDocumentService.catalog());
    }

    /**
     * 根据文档来源标识读取已经存储的分块内容，供管理页面预览。
     *
     * @param source 文档来源标识，通常为导入时记录的文件名或来源路径
     * @return 文档元数据和持久化分块预览
     */
    @GetMapping("/documents/chunks")
    public ApiResponse<ChunkPreviewResponse> documentChunks(@RequestParam("source") String source) {
        return ApiResponse.success(knowledgeDocumentService.storedDocument(source));
    }

    /**
     * 预览文本按指定策略切分后的结果，不写入向量库。
     *
     * @param request 待预览文本、元数据、内容类型和切分策略
     * @return 文档元数据和分块预览
     */
    @PostMapping("/documents/preview")
    public ApiResponse<ChunkPreviewResponse> previewDocument(@RequestBody AddDocumentRequest request) {
        return ApiResponse.success(knowledgeDocumentService.previewText(request));
    }

    /**
     * 解析并预览 PDF 的分块结果，不写入向量库。
     *
     * @param file 待解析的 PDF 文件
     * @param department 可选部门元数据，缺省时按目录配置解析
     * @param role 可选角色元数据，缺省时按目录配置解析
     * @param version 可选文档版本，缺省时按目录配置解析
     * @param documentId 可选文档唯一编号
     * @param strategy 可选切分策略，缺省时使用服务默认策略
     * @return PDF 元数据和分块预览
     * @throws IOException PDF 读取或解析失败时抛出
     */
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

    /**
     * 批量切分、向量化并写入多篇文本知识文档。
     *
     * @param request 待导入的文本知识文档集合
     * @return 所有文档写入后生成的向量分块编号
     */
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
     * 上传、解析并导入单个 PDF。元数据优先使用请求参数，否则按文件名从
     * application.yml 的 rag.catalog 解析。
     *
     * @param file 待导入的 PDF 文件
     * @param department 可选部门元数据，用于覆盖目录配置
     * @param role 可选角色元数据，用于覆盖目录配置
     * @param version 可选文档版本，用于覆盖目录配置
     * @param documentId 可选文档唯一编号
     * @param strategy 可选切分策略，缺省时使用服务默认策略
     * @return 文件名、分块数量和导入状态
     * @throws IOException PDF 读取或解析失败时抛出
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
     * 批量上传和导入 PDF，每个文件按 catalog 中的文件名自动匹配元数据。
     *
     * @param files 待批量导入的 PDF 文件数组
     * @return 每个 PDF 的文件名、分块数量和导入状态
     * @throws IOException 任一 PDF 读取或解析失败时抛出
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
     * 一键导入 classpath:data 目录下的全部内置 PDF 知识文档。
     *
     * @return 每个内置 PDF 的文件名、分块数量和导入状态
     * @throws IOException 内置 PDF 读取或解析失败时抛出
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

    /**
     * 使用请求体执行向量召回，可按角色过滤并选择是否重排结果。
     *
     * @param request 查询文本、召回数量、角色过滤条件和重排开关
     * @return 命中的知识分块、相关度和检索元数据
     */
    @PostMapping("/search")
    public ApiResponse<SearchResponse> search(@RequestBody SearchRequest request) {
        log.info("POST /vector/milvus/search - queryLength={}, topK={}, filtered={}, enableRerank={}",
                request.getQuery() != null ? request.getQuery().length() : 0,
                request.getTopK(), request.getRoleFilter() != null, request.getEnableRerank());

        SearchResponse response = ragService.search(request);

        return ApiResponse.success(response);
    }

    /**
     * 执行完整 RAG 问答：Milvus 召回、qwen3-rerank 重排并由 Qwen 生成回答。
     *
     * @param request 用户问题、召回数量、角色过滤条件和重排开关
     * @return 生成的回答、引用证据和检索元数据
     */
    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody SearchRequest request) {
        log.info("POST /vector/milvus/ask - queryLength={}, filtered={}",
                request.getQuery() != null ? request.getQuery().length() : 0,
                request.getRoleFilter() != null);
        AskResponse response = ragAskService.ask(request);
        return ApiResponse.success(response);
    }

    /**
     * 通过简单查询参数执行无额外过滤条件的向量检索。
     *
     * @param q 查询文本
     * @param topK 最大召回数量，默认 5
     * @return 命中的知识分块、相关度和检索元数据
     */
    @GetMapping("/search")
    public ApiResponse<SearchResponse> searchGet(
            @RequestParam("q") String q,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        log.info("GET /vector/milvus/search - queryLength={}, topK={}", q.length(), topK);
        SearchResponse response = ragService.search(q, topK);
        return ApiResponse.success(response);
    }

    /**
     * 查询向量知识库的集合、文档和分块统计信息。
     *
     * @return 当前 RAG 存储统计数据
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(ragService.stats());
    }

    /**
     * 将 Controller 内部抛出的参数校验异常转换为统一的 HTTP 400 响应。
     *
     * @param exception 参数不合法异常
     * @return 包含校验错误原因的统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }
}
