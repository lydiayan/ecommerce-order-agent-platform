package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchResponse;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.response.SearchResultsWrapper.IDScore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务：自定义 Milvus Schema，每个 metadata 字段独立为标量列，
 * 支持先标量过滤再向量检索。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    static final String COLLECTION_NAME = "mall_rag_v2";
    private static final int DIMENSION = 1536;

    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;
    private final DocumentService documentService;

    private volatile boolean collectionReady = false;

    public RagService(MilvusServiceClient milvusClient,
                      EmbeddingModel embeddingModel,
                      DocumentService documentService) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.documentService = documentService;
    }

    // ==================== 集合初始化 ====================

    @PostConstruct
    void ensureCollection() {
        // 检查集合是否存在
        DescribeCollectionParam describeParam = DescribeCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        R<DescribeCollectionResponse> describeResult = milvusClient.describeCollection(describeParam);

        if (describeResult.getStatus() == R.Status.Success.getCode()) {
            log.info("Collection already exists: {}", COLLECTION_NAME);
            collectionReady = true;
            return;
        }

        // ── 定义字段 ──
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        FieldType sourceField = FieldType.newBuilder()
                .withName("source")
                .withDataType(DataType.VarChar)
                .withMaxLength(256)
                .build();

        FieldType departmentField = FieldType.newBuilder()
                .withName("department")
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .build();

        FieldType roleField = FieldType.newBuilder()
                .withName("role")
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .build();

        FieldType versionField = FieldType.newBuilder()
                .withName("version")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType createTimeField = FieldType.newBuilder()
                .withName("create_time")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(DIMENSION)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("RAG collection with typed metadata fields")
                .addFieldType(idField)
                .addFieldType(contentField)
                .addFieldType(sourceField)
                .addFieldType(departmentField)
                .addFieldType(roleField)
                .addFieldType(versionField)
                .addFieldType(createTimeField)
                .addFieldType(embeddingField)
                .build();

        R<RpcStatus> createResult = milvusClient.createCollection(createParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to create collection {}: {}", COLLECTION_NAME, createResult.getMessage());
            return;
        }
        log.info("Created collection: {}", COLLECTION_NAME);

        // ── 向量索引 ──
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build();
        R<RpcStatus> vectorIndexResult = milvusClient.createIndex(vectorIndexParam);
        if (vectorIndexResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Failed to create embedding index: {}", vectorIndexResult.getMessage());
        } else {
            log.info("Created embedding index on {}", COLLECTION_NAME);
        }

        // ── 标量索引（高频过滤字段） ──
        createScalarIndex("role");
        createScalarIndex("department");
        createScalarIndex("source");

        // ── 加载集合到内存 ──
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        milvusClient.loadCollection(loadParam);

        collectionReady = true;
        log.info("Collection {} initialized and loaded", COLLECTION_NAME);
    }

    private void createScalarIndex(String fieldName) {
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName(fieldName)
                .withIndexType(IndexType.TRIE)  // 字符串标量索引
                .build();
        R<RpcStatus> result = milvusClient.createIndex(indexParam);
        if (result.getStatus() == R.Status.Success.getCode()) {
            log.info("Created scalar index on {}.{}", COLLECTION_NAME, fieldName);
        } else {
            log.warn("Scalar index on {} may already exist: {}", fieldName, result.getMessage());
        }
    }

    // ==================== 写操作 ====================

    /**
     * 添加文档到向量库（自动分割 + embedding + 存储）
     */
    public List<String> addDocument(String text, DocumentMetadata metadata) {
        log.info("Adding document to vector store, text length={}", text.length());

        List<Document> chunks = documentService.splitText(text, metadata);
        log.info("Document split into {} chunks", chunks.size());

        return insertChunks(chunks, metadata);
    }

    /**
     * 批量添加文档
     */
    public List<String> addDocuments(List<DocumentService.DocumentInput> inputs) {
        log.info("Adding {} documents to vector store", inputs.size());

        List<Document> allChunks = documentService.splitTexts(inputs);
        log.info("Documents split into {} chunks", allChunks.size());

        // 按原始 metadata 分组插入
        List<String> allIds = new ArrayList<>();
        for (DocumentService.DocumentInput input : inputs) {
            List<Document> inputChunks = documentService.splitText(input.text(), input.metadata());
            allIds.addAll(insertChunks(inputChunks, input.metadata()));
        }
        return allIds;
    }

    /**
     * 直接添加已处理的文档列表（PDF 场景）
     * 从每个文档的 Map metadata 中还原 DocumentMetadata
     */
    public List<String> addProcessedDocuments(List<Document> documents) {
        log.info("Adding {} processed documents to vector store", documents.size());

        List<String> ids = new ArrayList<>();
        for (Document doc : documents) {
            DocumentMetadata meta = DocumentMetadata.fromMap(doc.getMetadata());
            ids.add(insertOne(doc, meta));
        }
        log.info("Documents stored with IDs: {}", ids);
        return ids;
    }

    // ==================== 内部：插入 ====================

    private List<String> insertChunks(List<Document> chunks, DocumentMetadata metadata) {
        List<String> ids = new ArrayList<>();
        for (Document chunk : chunks) {
            ids.add(insertOne(chunk, metadata));
        }
        log.debug("Inserted {} chunks", ids.size());
        return ids;
    }

    private String insertOne(Document chunk, DocumentMetadata metadata) {
        if (!collectionReady) {
            throw new IllegalStateException("Collection " + COLLECTION_NAME + " is not ready");
        }

        String id = chunk.getId();
        String text = chunk.getText();
        List<Float> vector = generateEmbedding(text);

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(List.of(
                        new InsertParam.Field("id", List.of(id)),
                        new InsertParam.Field("content", List.of(text)),
                        new InsertParam.Field("source", List.of(nn(metadata.getSource()))),
                        new InsertParam.Field("department", List.of(nn(metadata.getDepartment()))),
                        new InsertParam.Field("role", List.of(nn(metadata.getRole()))),
                        new InsertParam.Field("version", List.of(nn(metadata.getVersion()))),
                        new InsertParam.Field("create_time", List.of(nn(metadata.getCreateTime()))),
                        new InsertParam.Field("embedding", List.of(vector))
                ))
                .build();

        R<MutationResult> result = milvusClient.insert(insertParam);
        if (result.getStatus() == R.Status.Success.getCode()) {
            log.debug("Inserted chunk {}", id);
        } else {
            log.error("Failed to insert chunk {}: {}", id, result.getMessage());
        }
        return id;
    }

    // ==================== 搜索 ====================

    /**
     * 语义搜索（不带标量过滤）
     */
    public SearchResponse search(String query, int topK) {
        return searchInternal(query, topK, 0.0, null);
    }

    /**
     * 带相似度阈值的语义搜索（不带标量过滤）
     */
    public SearchResponse searchWithThreshold(String query, int topK, double similarityThreshold) {
        return searchInternal(query, topK, similarityThreshold, null);
    }

    /**
     * 带标量过滤的语义搜索
     *
     * @param query      查询文本
     * @param topK       top-K
     * @param threshold  相似度阈值
     * @param filterExpr Milvus 标量过滤表达式，如 {@code role == "客服"}，传 null 表示不过滤
     */
    public SearchResponse searchWithFilter(String query, int topK, double threshold, String filterExpr) {
        return searchInternal(query, topK, threshold, filterExpr);
    }

    private SearchResponse searchInternal(String query, int topK, double threshold, String filterExpr) {
        if (!collectionReady) {
            log.warn("Collection not ready, returning empty results");
            return new SearchResponse(query, 0, List.of());
        }

        log.info("Search: query='{}', topK={}, threshold={}, filter='{}'", query, topK, threshold, filterExpr);

        List<Float> queryVector = generateEmbedding(query);

        List<String> outputFields = List.of("id", "content", "source", "department",
                "role", "version", "create_time");

        SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(List.of(queryVector))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\":16}")
                .withOutFields(outputFields)
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY);

        if (filterExpr != null && !filterExpr.isBlank()) {
            searchBuilder.withExpr(filterExpr);
        }

        R<SearchResults> searchResult = milvusClient.search(searchBuilder.build());
        if (searchResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Search failed: {}", searchResult.getMessage());
            return new SearchResponse(query, 0, List.of());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
        List<SearchResponse.SearchHit> hits = new ArrayList<>();

        try {
            List<String> ids = (List<String>) wrapper.getFieldData("id", 0);
            List<String> contents = (List<String>) wrapper.getFieldData("content", 0);
            List<String> sources = (List<String>) wrapper.getFieldData("source", 0);
            List<String> departments = (List<String>) wrapper.getFieldData("department", 0);
            List<String> roles = (List<String>) wrapper.getFieldData("role", 0);
            List<String> versions = (List<String>) wrapper.getFieldData("version", 0);
            List<String> createTimes = (List<String>) wrapper.getFieldData("create_time", 0);
            List<IDScore> idScores = wrapper.getIDScore(0);

            // 构建 ID → score 映射
            Map<String, Float> scoreMap = new LinkedHashMap<>();
            if (idScores != null) {
                for (IDScore is : idScores) {
                    scoreMap.put(is.getStrID(), is.getScore());
                }
            }

            for (int i = 0; i < ids.size(); i++) {
                String hitId = safeGet(ids, i, "");
                double score = scoreMap.getOrDefault(hitId, 0.0f);
                if (score < threshold) {
                    continue;
                }

                DocumentMetadata meta = new DocumentMetadata();
                meta.setSource(safeGet(sources, i, null));
                meta.setDepartment(safeGet(departments, i, null));
                meta.setRole(safeGet(roles, i, null));
                meta.setVersion(safeGet(versions, i, null));
                meta.setCreateTime(safeGet(createTimes, i, null));

                hits.add(new SearchResponse.SearchHit(
                        safeGet(ids, i, ""),
                        safeGet(contents, i, ""),
                        score,
                        meta
                ));
            }
        } catch (Exception e) {
            log.warn("Error parsing search results: {}", e.getMessage());
        }

        log.info("Search returned {} hits after threshold filter", hits.size());
        return new SearchResponse(query, hits.size(), hits);
    }

    // ==================== 工具方法 ====================

    /**
     * 构建 Milvus 标量过滤表达式。各过滤条件之间为 AND 关系。
     */
    public static String buildFilterExpression(String source, String department,
                                                String role, String version) {
        List<String> parts = new ArrayList<>();
        if (source != null && !source.isBlank()) {
            parts.add("source == \"" + escapeExpr(source) + "\"");
        }
        if (department != null && !department.isBlank()) {
            parts.add("department == \"" + escapeExpr(department) + "\"");
        }
        if (role != null && !role.isBlank()) {
            parts.add("role == \"" + escapeExpr(role) + "\"");
        }
        if (version != null && !version.isBlank()) {
            parts.add("version == \"" + escapeExpr(version) + "\"");
        }
        return parts.isEmpty() ? null : String.join(" && ", parts);
    }

    /** 转义 Milvus 表达式中的特殊字符 */
    private static String escapeExpr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<Float> generateEmbedding(String text) {
        float[] vector = embeddingModel.embed(text);
        List<Float> result = new ArrayList<>(vector.length);
        for (float v : vector) {
            result.add(v);
        }
        return result;
    }

    private String nn(String value) {
        return value != null ? value : "";
    }

    private <T> T safeGet(List<T> list, int index, T defaultValue) {
        if (list != null && index < list.size()) {
            T v = list.get(index);
            return v != null ? v : defaultValue;
        }
        return defaultValue;
    }

    /**
     * 获取向量库状态信息
     */
    public Map<String, Object> stats() {
        return Map.of(
                "status", collectionReady ? "connected" : "not_ready",
                "collection", COLLECTION_NAME,
                "service", "Milvus RAG Service (custom schema)"
        );
    }
}
