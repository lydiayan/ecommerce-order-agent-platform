package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索服务：提供向量检索和语义搜索能力
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final MilvusVectorStore vectorStore;
    private final DocumentService documentService;

    public RagService(MilvusVectorStore vectorStore, DocumentService documentService) {
        this.vectorStore = vectorStore;
        this.documentService = documentService;
    }

    /**
     * 添加文档到向量库（自动分割 + embedding + 存储）
     *
     * @param text     文档文本
     * @param metadata 元数据
     * @return 存储的文档 ID 列表
     */
    public List<String> addDocument(String text, Map<String, Object> metadata) {
        log.info("Adding document to vector store, text length={}", text.length());

        // 1. 文本分割
        List<Document> documents = documentService.splitText(text, metadata);
        log.info("Document split into {} chunks", documents.size());

        // 2. 生成 embedding 并存储到 Milvus
        vectorStore.add(documents);

        // 3. 返回存储的文档 ID
        List<String> ids = documents.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
        log.info("Documents stored with IDs: {}", ids);
        return ids;
    }

    /**
     * 批量添加文档
     *
     * @param inputs 文档输入列表
     * @return 存储的文档 ID 列表
     */
    public List<String> addDocuments(List<DocumentService.DocumentInput> inputs) {
        log.info("Adding {} documents to vector store", inputs.size());

        List<Document> documents = documentService.splitTexts(inputs);
        log.info("Documents split into {} chunks", documents.size());

        vectorStore.add(documents);

        List<String> ids = documents.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
        log.info("Documents stored with IDs: {}", ids);
        return ids;
    }

    /**
     * 直接添加已处理的文档列表到向量库
     *
     * @param documents 已处理的 Spring AI Document 列表
     * @return 存储的文档 ID 列表
     */
    public List<String> addProcessedDocuments(List<Document> documents) {
        log.info("Adding {} processed documents to vector store", documents.size());
        vectorStore.add(documents);

        List<String> ids = documents.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
        log.info("Documents stored with IDs: {}", ids);
        return ids;
    }

    /**
     * 语义搜索
     *
     * @param query 查询文本
     * @param topK  返回 top-K 结果
     * @return 搜索结果
     */
    public SearchResponse search(String query, int topK) {
        log.info("Searching vector store: query='{}', topK={}", query, topK);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.0)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        log.info("Search returned {} results", results.size());

        List<SearchResponse.SearchHit> hits = results.stream()
                .map(doc -> new SearchResponse.SearchHit(
                        doc.getId(),
                        doc.getText(),
                        (double) doc.getMetadata().getOrDefault("distance", 0.0),
                        doc.getMetadata()
                ))
                .collect(Collectors.toList());

        return new SearchResponse(query, hits.size(), hits);
    }

    /**
     * 带相似度阈值的语义搜索
     *
     * @param query               查询文本
     * @param topK                返回 top-K 结果
     * @param similarityThreshold 相似度阈值 (0.0 ~ 1.0)，低于此值的将被过滤
     * @return 搜索结果
     */
    public SearchResponse searchWithThreshold(String query, int topK, double similarityThreshold) {
        log.info("Searching vector store with threshold: query='{}', topK={}, threshold={}",
                query, topK, similarityThreshold);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        List<SearchResponse.SearchHit> hits = results.stream()
                .map(doc -> new SearchResponse.SearchHit(
                        doc.getId(),
                        doc.getText(),
                        (double) doc.getMetadata().getOrDefault("distance", 0.0),
                        doc.getMetadata()
                ))
                .collect(Collectors.toList());

        return new SearchResponse(query, hits.size(), hits);
    }

    /**
     * 获取向量库状态信息
     */
    public Map<String, Object> stats() {
        return Map.of(
                "status", "connected",
                "service", "Milvus RAG Service",
                "description", "Milvus vector store with deepseek-embedding is operational"
        );
    }
}
