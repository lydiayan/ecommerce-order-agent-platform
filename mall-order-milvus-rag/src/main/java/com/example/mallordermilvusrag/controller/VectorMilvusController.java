package com.example.mallordermilvusrag.controller;

import com.example.mallordermilvusrag.dto.*;
import com.example.mallordermilvusrag.service.DocumentService;
import com.example.mallordermilvusrag.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Milvus RAG 向量检索 REST API
 * <p>
 * 提供文档管理、语义搜索等 RAG 相关功能。
 * 使用 Milvus 作为向量数据库，deepseek-embedding 作为嵌入模型。
 */
@RestController
@RequestMapping("/vector/milvus")
public class VectorMilvusController {

    private static final Logger log = LoggerFactory.getLogger(VectorMilvusController.class);

    private final RagService ragService;
    private final DocumentService documentService;

    public VectorMilvusController(RagService ragService, DocumentService documentService) {
        this.ragService = ragService;
        this.documentService = documentService;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Milvus RAG service is running");
    }

    /**
     * 添加单条文本到向量库
     * <p>
     * 请求体示例:
     * <pre>
     * {
     *   "text": "退货流程：已发货的订单不可取消，转为售后退货流程，需用户提供拒签原因",
     *   "metadata": {
     *     "category": "return_policy",
     *     "source": "manual"
     *   }
     * }
     * </pre>
     *
     * @param request 包含文本内容和可选元数据
     * @return 存储的文档块 ID 列表
     */
    @PostMapping("/documents")
    public ApiResponse<List<String>> addDocument(@RequestBody AddDocumentRequest request) {
        log.info("POST /vector/milvus/documents - adding document");
        List<String> ids = ragService.addDocument(request.getText(), request.getMetadata());
        return ApiResponse.success("Document added successfully, split into " + ids.size() + " chunks", ids);
    }

    /**
     * 批量添加文档到向量库
     *
     * @param request 批量文档请求
     * @return 所有文档块 ID 列表
     */
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
     * 上传 PDF 文件并导入到向量库
     *
     * @param file     PDF 文件（multipart/form-data）
     * @param category 文档分类（可选参数）
     * @return 所有文档块 ID 列表
     */
    @PostMapping(value = "/documents/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<String>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category",required = false) String category) throws IOException {

        log.info("POST /vector/milvus/documents/pdf - uploading file: {}", file.getOriginalFilename());

        Map<String, Object> metadata = Map.of(
                "source_file", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                "category", category != null ? category : "default",
                "content_type", file.getContentType() != null ? file.getContentType() : "application/pdf"
        );

        // 解析 PDF 并分割文档
        List<org.springframework.ai.document.Document> documents =
                documentService.parsePdf(file.getBytes(), metadata);

        // 存储到向量库
        List<String> ids = ragService.addProcessedDocuments(documents);

        return ApiResponse.success("PDF processed, total " + ids.size() + " chunks", ids);
    }

    /**
     * 语义搜索（POST 方式，支持更多参数）
     * <p>
     * 请求体示例:
     * <pre>
     * {
     *   "query": "退货流程是什么？",
     *   "topK": 5,
     *   "similarityThreshold": 0.5
     * }
     * </pre>
     *
     * @param request 搜索请求
     * @return 搜索结果列表
     */
    @PostMapping("/search")
    public ApiResponse<SearchResponse> search(@RequestBody SearchRequest request) {
        log.info("POST /vector/milvus/search - query='{}', topK={}", request.getQuery(), request.getTopK());

        SearchResponse response;
        if (request.getSimilarityThreshold() != null) {
            response = ragService.searchWithThreshold(
                    request.getQuery(), request.getTopK(), request.getSimilarityThreshold());
        } else {
            response = ragService.search(request.getQuery(), request.getTopK());
        }

        return ApiResponse.success(response);
    }

    /**
     * 语义搜索（GET 方式，方便快速测试）
     *
     * @param q    查询文本
     * @param topK 返回条数，默认 5
     * @return 搜索结果列表
     */
    @GetMapping("/search")
    public ApiResponse<SearchResponse> searchGet(@RequestParam("q") String q, @RequestParam(value = "topK", defaultValue = "5") int topK) {
        log.info("GET /vector/milvus/search - query='{}', topK={}", q, topK);
        SearchResponse response = ragService.search(q, topK);
        return ApiResponse.success(response);
    }

    /**
     * 获取 Milvus RAG 服务状态和统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(ragService.stats());
    }
}
