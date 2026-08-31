package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchRequest;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.splitter.api.RagContentType;
import com.example.mallordermilvusrag.splitter.api.RagSplitStrategy;
import com.example.mallordermilvusrag.splitter.model.ChunkLevel;
import com.example.mallordermilvusrag.splitter.model.RagChunkMetadata;
import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.TracePrivacy;
import com.example.mallorderobservability.trace.RagTraceService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.response.SearchResultsWrapper.IDScore;
import io.milvus.response.QueryResultsWrapper;
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

    private static final List<String> CHUNK_OUTPUT_FIELDS = List.of(
            "id", "content", "source", "department", "role", "version", "create_time",
            "document_id", "parent_id", "chunk_level", "chunk_index", "total_chunks",
            "strategy", "content_type", "title_path", "start_offset", "end_offset");
    private static final List<String> CATALOG_OUTPUT_FIELDS = List.of(
            "id", "source", "department", "role", "version", "create_time", "document_id",
            "chunk_level", "chunk_index", "total_chunks", "strategy", "content_type");
    private static final long MAX_ADMIN_CHUNK_ROWS = 16_384L;

    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;
    private final DocumentService documentService;
    private final DashScopeRerankService rerankService;
    private final RagDocumentProperties ragDocumentProperties;
    private final RagTraceService ragTraceService;
    private final String collectionName;
    private final int dimensions;

    private volatile boolean collectionReady = false;

    public RagService(MilvusServiceClient milvusClient,
                      EmbeddingModel embeddingModel,
                      DocumentService documentService,
                      DashScopeRerankService rerankService,
                      RagDocumentProperties ragDocumentProperties,
                      RagTraceService ragTraceService) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.documentService = documentService;
        this.rerankService = rerankService;
        this.ragDocumentProperties = ragDocumentProperties;
        this.ragTraceService = ragTraceService;
        this.collectionName = ragDocumentProperties.getCollectionName();
        this.dimensions = ragDocumentProperties.getDimensions();
    }

    // ==================== 集合初始化 ====================

    @PostConstruct
    void ensureCollection() {
        R<Boolean> exists = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (exists.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Failed to check collection " + collectionName);
        }

        // 检查集合是否存在
        DescribeCollectionParam describeParam = DescribeCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();

        if (Boolean.TRUE.equals(exists.getData())) {
            R<DescribeCollectionResponse> describeResult = milvusClient.describeCollection(describeParam);
            if (describeResult.getStatus() != R.Status.Success.getCode()) {
                throw new IllegalStateException("Failed to describe collection " + collectionName);
            }
            Set<String> fields = describeResult.getData().getSchema().getFieldsList().stream()
                    .map(field -> field.getName()).collect(Collectors.toSet());
            Set<String> missing = new LinkedHashSet<>(CHUNK_OUTPUT_FIELDS);
            missing.add("embedding");
            missing.removeAll(fields);
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Collection " + collectionName
                        + " uses an incompatible schema; missing fields: " + missing);
            }
            log.info("Collection already exists: {}", collectionName);
            loadCollection();
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

        FieldType documentIdField = varcharField("document_id", 64);
        FieldType parentIdField = varcharField("parent_id", 64);
        FieldType chunkLevelField = varcharField("chunk_level", 16);
        FieldType strategyField = varcharField("strategy", 32);
        FieldType contentTypeField = varcharField("content_type", 32);
        FieldType titlePathField = varcharField("title_path", 2048);
        FieldType chunkIndexField = int64Field("chunk_index");
        FieldType totalChunksField = int64Field("total_chunks");
        FieldType startOffsetField = int64Field("start_offset");
        FieldType endOffsetField = int64Field("end_offset");

        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dimensions)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("RAG v3 collection with typed splitter metadata and parent-child chunks")
                .addFieldType(idField)
                .addFieldType(contentField)
                .addFieldType(sourceField)
                .addFieldType(departmentField)
                .addFieldType(roleField)
                .addFieldType(versionField)
                .addFieldType(createTimeField)
                .addFieldType(documentIdField)
                .addFieldType(parentIdField)
                .addFieldType(chunkLevelField)
                .addFieldType(chunkIndexField)
                .addFieldType(totalChunksField)
                .addFieldType(strategyField)
                .addFieldType(contentTypeField)
                .addFieldType(titlePathField)
                .addFieldType(startOffsetField)
                .addFieldType(endOffsetField)
                .addFieldType(embeddingField)
                .build();

        R<RpcStatus> createResult = milvusClient.createCollection(createParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to create collection {}: {}", collectionName, createResult.getMessage());
            return;
        }
        log.info("Created collection: {}", collectionName);

        // ── 向量索引 ──
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build();
        R<RpcStatus> vectorIndexResult = milvusClient.createIndex(vectorIndexParam);
        if (vectorIndexResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Failed to create embedding index: {}", vectorIndexResult.getMessage());
        } else {
            log.info("Created embedding index on {}", collectionName);
        }

        // ── 标量索引（高频过滤字段） ──
        createScalarIndex("role");
        createScalarIndex("department");
        createScalarIndex("source");
        createScalarIndex("document_id");
        createScalarIndex("parent_id");
        createScalarIndex("chunk_level");

        // ── 加载集合到内存 ──
        loadCollection();

        collectionReady = true;
        log.info("Collection {} initialized and loaded", collectionName);
    }

    private void loadCollection() {
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(true)
                .withSyncLoadWaitingInterval(200L)
                .withSyncLoadWaitingTimeout(30L)
                .build();
        R<RpcStatus> loadResult = milvusClient.loadCollection(loadParam);
        if (loadResult.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Failed to load collection " + collectionName
                    + ": " + loadResult.getMessage());
        }
    }

    private void createScalarIndex(String fieldName) {
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(fieldName)
                .withIndexType(IndexType.TRIE)  // 字符串标量索引
                .build();
        R<RpcStatus> result = milvusClient.createIndex(indexParam);
        if (result.getStatus() == R.Status.Success.getCode()) {
            log.info("Created scalar index on {}.{}", collectionName, fieldName);
        } else {
            log.warn("Scalar index on {} may already exist: {}", fieldName, result.getMessage());
        }
    }

    private static FieldType varcharField(String name, int maxLength) {
        return FieldType.newBuilder().withName(name).withDataType(DataType.VarChar)
                .withMaxLength(maxLength).build();
    }

    private static FieldType int64Field(String name) {
        return FieldType.newBuilder().withName(name).withDataType(DataType.Int64).build();
    }

    // ==================== 写操作 ====================

    /**
     * 添加文档到向量库（自动分割 + embedding + 存储）
     */
    public List<String> addDocument(String text, DocumentMetadata metadata) {
        return addDocument(text, metadata, null, null, null);
    }

    public List<String> addDocument(String text, DocumentMetadata metadata, String documentId,
                                    RagSplitStrategy strategy, RagContentType contentType) {
        log.info("Adding document to vector store, text length={}", text.length());

        List<Document> chunks = documentService.splitText(text, metadata, documentId, strategy, contentType);
        log.info("Document split into {} chunks", chunks.size());

        return replaceChunks(chunks);
    }

    /**
     * 批量添加文档
     */
    public List<String> addDocuments(List<DocumentService.DocumentInput> inputs) {
        log.info("Adding {} documents to vector store", inputs.size());

        List<String> allIds = new ArrayList<>();
        for (DocumentService.DocumentInput input : inputs) {
            List<Document> inputChunks = documentService.splitText(input.text(), input.metadata(),
                    input.documentId(), input.strategy(), input.contentType());
            allIds.addAll(replaceChunks(inputChunks));
        }
        return allIds;
    }

    /**
     * 直接添加已处理的文档列表（PDF 场景）
     * 从每个文档的 Map metadata 中还原 DocumentMetadata
     */
    public List<String> addProcessedDocuments(List<Document> documents) {
        log.info("Adding {} processed documents to vector store", documents.size());

        Map<String, List<Document>> byDocument = documents.stream().collect(Collectors.groupingBy(
                document -> stringMetadata(document.getMetadata(), RagChunkMetadata.DOCUMENT_ID, ""),
                LinkedHashMap::new, Collectors.toList()));
        List<String> ids = new ArrayList<>();
        for (List<Document> chunks : byDocument.values()) {
            ids.addAll(replaceChunks(chunks));
        }
        log.info("Documents stored with IDs: {}", ids);
        return ids;
    }

    // ==================== 内部：插入 ====================

    private List<String> insertChunks(List<Document> chunks) {
        List<String> ids = new ArrayList<>();
        for (Document chunk : chunks) {
            ids.add(insertOne(chunk));
        }
        log.debug("Inserted {} chunks", ids.size());
        return ids;
    }

    private List<String> replaceChunks(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        String documentId = stringMetadata(chunks.get(0).getMetadata(), RagChunkMetadata.DOCUMENT_ID, "");
        if (documentId.isBlank()) {
            return insertChunks(chunks);
        }
        Set<String> oldIds = queryChunkIds(documentId);
        List<String> newIds = insertChunks(chunks);
        oldIds.removeAll(newIds);
        deleteChunkIds(oldIds);
        return newIds;
    }

    private Set<String> queryChunkIds(String documentId) {
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr("document_id == \"" + escapeExpr(documentId) + "\"")
                .withOutFields(List.of("id"))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        R<QueryResults> result = milvusClient.query(param);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Failed to query existing chunks for document " + documentId);
        }
        Set<String> ids = new LinkedHashSet<>();
        for (QueryResultsWrapper.RowRecord row : new QueryResultsWrapper(result.getData()).getRowRecords()) {
            ids.add(asString(row.getFieldValues().get("id")));
        }
        return ids;
    }

    private void deleteChunkIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>(ids);
        for (int start = 0; start < values.size(); start += 500) {
            int end = Math.min(start + 500, values.size());
            String expr = values.subList(start, end).stream()
                    .map(id -> "\"" + escapeExpr(id) + "\"")
                    .collect(Collectors.joining(",", "id in [", "]"));
            R<MutationResult> result = milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(collectionName).withExpr(expr).build());
            if (result.getStatus() != R.Status.Success.getCode()) {
                throw new IllegalStateException("Failed to remove stale chunks");
            }
        }
    }

    private String insertOne(Document chunk) {
        if (!collectionReady) {
            throw new IllegalStateException("Collection " + collectionName + " is not ready");
        }

        String id = chunk.getId();
        String text = chunk.getText();
        Map<String, Object> values = chunk.getMetadata();
        DocumentMetadata metadata = DocumentMetadata.fromMap(values);
        String chunkLevel = stringMetadata(values, RagChunkMetadata.CHUNK_LEVEL, ChunkLevel.STANDALONE.name());
        List<Float> vector = ChunkLevel.PARENT.name().equals(chunkLevel)
                ? Collections.nCopies(dimensions, 0.0f) : generateEmbedding(text);

        UpsertParam upsertParam = UpsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(List.of(
                        new InsertParam.Field("id", List.of(id)),
                        new InsertParam.Field("content", List.of(text)),
                        new InsertParam.Field("source", List.of(nn(metadata.getSource()))),
                        new InsertParam.Field("department", List.of(nn(metadata.getDepartment()))),
                        new InsertParam.Field("role", List.of(nn(metadata.getRole()))),
                        new InsertParam.Field("version", List.of(nn(metadata.getVersion()))),
                        new InsertParam.Field("create_time", List.of(nn(metadata.getCreateTime()))),
                        new InsertParam.Field("document_id", List.of(stringMetadata(values, RagChunkMetadata.DOCUMENT_ID, ""))),
                        new InsertParam.Field("parent_id", List.of(stringMetadata(values, RagChunkMetadata.PARENT_ID, ""))),
                        new InsertParam.Field("chunk_level", List.of(chunkLevel)),
                        new InsertParam.Field("chunk_index", List.of(longMetadata(values, RagChunkMetadata.CHUNK_INDEX))),
                        new InsertParam.Field("total_chunks", List.of(longMetadata(values, RagChunkMetadata.TOTAL_CHUNKS))),
                        new InsertParam.Field("strategy", List.of(stringMetadata(values, RagChunkMetadata.STRATEGY, ""))),
                        new InsertParam.Field("content_type", List.of(stringMetadata(values, RagChunkMetadata.CONTENT_TYPE, ""))),
                        new InsertParam.Field("title_path", List.of(stringMetadata(values, RagChunkMetadata.TITLE_PATH, ""))),
                        new InsertParam.Field("start_offset", List.of(longMetadata(values, RagChunkMetadata.START_OFFSET))),
                        new InsertParam.Field("end_offset", List.of(longMetadata(values, RagChunkMetadata.END_OFFSET))),
                        new InsertParam.Field("embedding", List.of(vector))
                ))
                .build();

        R<MutationResult> result = milvusClient.upsert(upsertParam);
        if (result.getStatus() == R.Status.Success.getCode()) {
            log.debug("Upserted chunk {}", id);
        } else {
            throw new IllegalStateException("Failed to upsert chunk " + id + ": " + result.getMessage());
        }
        return id;
    }

    // ==================== 搜索 ====================

    /**
     * 语义搜索（不带标量过滤）
     */
    public SearchResponse search(String query, int topK) {
        SearchRequest request = new SearchRequest(query, topK);
        return search(request);
    }

    /**
     * 带相似度阈值的语义搜索（不带标量过滤）
     */
    public SearchResponse searchWithThreshold(String query, int topK, double similarityThreshold) {
        SearchRequest request = new SearchRequest(query, topK);
        request.setSimilarityThreshold(similarityThreshold);
        return search(request);
    }

    /**
     * 带标量过滤的语义搜索
     */
    public SearchResponse searchWithFilter(String query, int topK, double threshold, String filterExpr) {
        SearchRequest request = new SearchRequest(query, topK);
        request.setSimilarityThreshold(threshold);
        if (!ragTraceService.isEnabled()) {
            return searchInternal(request, filterExpr, RagTraceScope.noop());
        }
        return search(request);
    }

    /**
     * 完整搜索：Milvus 召回 →（可选）qwen3-rerank 重排序。
     */
    public SearchResponse search(SearchRequest request) {
        return search(request, null);
    }

    /**
     * 在已有 trace 下执行搜索（用于 rag.ask 的 retrieve 子 span）。
     */
    public SearchResponse search(SearchRequest request, RagTraceScope parentTrace) {
        String filterExpr = buildFilterExpression(
                request.getSourceFilter(),
                request.getDepartmentFilter(),
                request.getRoleFilter(),
                request.getVersionFilter(),
                request.getDepartmentFilters(),
                request.getRoleFilters()
        );
        if (!ragTraceService.isEnabled()) {
            return searchInternal(request, filterExpr, RagTraceScope.noop());
        }

        if (parentTrace != null && parentTrace != RagTraceScope.noop()) {
            SearchResponse response = searchInternal(request, filterExpr, parentTrace);
            parentTrace.attribute("hitCount", response.getTotalHits());
            parentTrace.attribute("reranked", response.isReranked());
            return response;
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("queryLength", request.getQuery() != null ? request.getQuery().length() : 0);
        attrs.put("queryFingerprint", TracePrivacy.fingerprint(request.getQuery()));
        attrs.put("topK", request.getTopK());
        attrs.put("enableRerank", request.getEnableRerank());

        try (RagTraceScope trace = ragTraceService.begin("rag.search", attrs)) {
            SearchResponse response = searchInternal(request, filterExpr, trace);
            response.setTraceId(trace.traceId());
            trace.attribute("hitCount", response.getTotalHits());
            trace.attribute("reranked", response.isReranked());
            return response;
        }
    }

    private SearchResponse searchInternal(SearchRequest request, String filterExpr, RagTraceScope trace) {
        if (!collectionReady) {
            log.warn("Collection not ready, returning empty results");
            return new SearchResponse(request.getQuery(), 0, false, List.of());
        }

        int topK = Math.max(request.getTopK(), 1);
        double vectorThreshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold() : 0.0;

        RagDocumentProperties.RerankProperties rerankProps = ragDocumentProperties.getRerank();
        boolean enableRerank = request.getEnableRerank() != null
                ? request.getEnableRerank() : rerankProps.isEnabled();
        int recallTopK = request.getRecallTopK() != null
                ? request.getRecallTopK()
                : topK * Math.max(rerankProps.getCandidateMultiplier(), 1);
        int rerankTopN = request.getRerankTopN() != null ? request.getRerankTopN() : topK;
        double rerankMinScore = request.getRerankMinScore() != null
                ? request.getRerankMinScore() : rerankProps.getMinScore();

        int milvusTopK = enableRerank ? Math.max(recallTopK, topK) : topK;

        log.info("Search: queryLength={}, topK={}, recallTopK={}, rerank={}, threshold={}, filterPresent={}",
                request.getQuery().length(), topK, milvusTopK, enableRerank, vectorThreshold,
                filterExpr != null && !filterExpr.isBlank());

        List<Float> queryVector = generateEmbedding(request.getQuery());

        List<SearchResponse.SearchHit> vectorHits;
        try (RagTraceScope milvusSpan = trace.child(RagTraceOperations.MILVUS, Map.of(
                "topK", milvusTopK,
                "filterApplied", filterExpr != null && !filterExpr.isBlank()))) {
            vectorHits = queryUniqueContexts(milvusTopK, vectorThreshold, filterExpr, queryVector);
            milvusSpan.attribute("recallCount", vectorHits.size());
        }

        if (!enableRerank || vectorHits.isEmpty()) {
            List<SearchResponse.SearchHit> hits = vectorHits.size() > topK
                    ? vectorHits.subList(0, topK) : vectorHits;
            return new SearchResponse(request.getQuery(), hits.size(), false, hits);
        }

        List<String> documents = vectorHits.stream()
                .map(SearchResponse.SearchHit::getContent)
                .toList();
        List<DashScopeRerankService.RerankItem> rerankItems;
        try (RagTraceScope rerankSpan = trace.child(RagTraceOperations.RERANK, Map.of(
                "candidateCount", documents.size(),
                "rerankTopN", rerankTopN))) {
            rerankItems = rerankService.rerank(request.getQuery(), documents, rerankTopN);
            rerankSpan.attribute("resultCount", rerankItems.size());
        }

        if (rerankItems.isEmpty()) {
            log.warn("Rerank skipped or failed, fallback to vector ranking");
            List<SearchResponse.SearchHit> hits = vectorHits.size() > topK
                    ? vectorHits.subList(0, topK) : vectorHits;
            return new SearchResponse(request.getQuery(), hits.size(), false, hits);
        }

        List<SearchResponse.SearchHit> rerankedHits = new ArrayList<>();
        for (DashScopeRerankService.RerankItem item : rerankItems) {
            if (item.score() < rerankMinScore) {
                continue;
            }
            SearchResponse.SearchHit original = vectorHits.get(item.index());
            original.setScore(item.score());
            original.setRerankScore(item.score());
            rerankedHits.add(original);
        }

        log.info("Search returned {} hit(s) after rerank", rerankedHits.size());
        return new SearchResponse(request.getQuery(), rerankedHits.size(), true, rerankedHits);
    }

    private List<SearchResponse.SearchHit> queryMilvus(int topK, double threshold,
                                                        String filterExpr, List<Float> queryVector) {

        SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(List.of(queryVector))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\":16}")
                .withOutFields(CHUNK_OUTPUT_FIELDS)
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY);

        String searchableChunks = "(chunk_level == \"CHILD\" || chunk_level == \"STANDALONE\")";
        searchBuilder.withExpr(filterExpr == null || filterExpr.isBlank()
                ? searchableChunks : searchableChunks + " && (" + filterExpr + ")");

        R<SearchResults> searchResult = milvusClient.search(searchBuilder.build());
        if (searchResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Search failed: {}", searchResult.getMessage());
            return List.of();
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
            List<String> documentIds = (List<String>) wrapper.getFieldData("document_id", 0);
            List<String> parentIds = (List<String>) wrapper.getFieldData("parent_id", 0);
            List<String> chunkLevels = (List<String>) wrapper.getFieldData("chunk_level", 0);
            List<Long> chunkIndexes = (List<Long>) wrapper.getFieldData("chunk_index", 0);
            List<Long> totalChunks = (List<Long>) wrapper.getFieldData("total_chunks", 0);
            List<String> strategies = (List<String>) wrapper.getFieldData("strategy", 0);
            List<String> contentTypes = (List<String>) wrapper.getFieldData("content_type", 0);
            List<String> titlePaths = (List<String>) wrapper.getFieldData("title_path", 0);
            List<Long> startOffsets = (List<Long>) wrapper.getFieldData("start_offset", 0);
            List<Long> endOffsets = (List<Long>) wrapper.getFieldData("end_offset", 0);
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

                SearchResponse.SearchHit hit = new SearchResponse.SearchHit(
                        safeGet(ids, i, ""),
                        safeGet(contents, i, ""),
                        score,
                        score,
                        null,
                        meta
                );
                hit.setDocumentId(safeGet(documentIds, i, ""));
                hit.setParentId(safeGet(parentIds, i, ""));
                hit.setChunkLevel(safeGet(chunkLevels, i, ChunkLevel.STANDALONE.name()));
                hit.setChunkIndex(safeGet(chunkIndexes, i, 0L).intValue());
                hit.setTotalChunks(safeGet(totalChunks, i, 0L).intValue());
                hit.setStrategy(safeGet(strategies, i, ""));
                hit.setContentType(safeGet(contentTypes, i, ""));
                hit.setTitlePath(safeGet(titlePaths, i, ""));
                hit.setStartOffset(safeGet(startOffsets, i, 0L));
                hit.setEndOffset(safeGet(endOffsets, i, 0L));
                hit.setMatchedChunks(List.of(toMatchedChunk(hit)));
                hits.add(hit);
            }
        } catch (Exception e) {
            log.warn("Error parsing search results: {}", e.getMessage());
        }

        log.info("Milvus returned {} hit(s) after vector threshold filter", hits.size());
        return hits;
    }

    private List<SearchResponse.SearchHit> queryUniqueContexts(int desiredCount, double threshold,
                                                                String filterExpr, List<Float> queryVector) {
        int recall = Math.max(desiredCount * 3, desiredCount);
        int maxRecall = Math.max(recall, 16_384);
        List<SearchResponse.SearchHit> aggregated = List.of();
        while (true) {
            List<SearchResponse.SearchHit> raw = queryMilvus(recall, threshold, filterExpr, queryVector);
            aggregated = aggregateParentContext(raw);
            if (aggregated.size() >= desiredCount || raw.size() < recall || recall >= maxRecall) {
                return aggregated;
            }
            recall = Math.min(recall * 2, maxRecall);
        }
    }

    private List<SearchResponse.SearchHit> aggregateParentContext(List<SearchResponse.SearchHit> hits) {
        Map<String, List<SearchResponse.SearchHit>> groupedChildren = new LinkedHashMap<>();
        List<SearchResponse.SearchHit> standalone = new ArrayList<>();
        for (SearchResponse.SearchHit hit : hits) {
            if (ChunkLevel.CHILD.name().equals(hit.getChunkLevel())
                    && hit.getParentId() != null && !hit.getParentId().isBlank()) {
                groupedChildren.computeIfAbsent(hit.getParentId(), ignored -> new ArrayList<>()).add(hit);
            } else {
                standalone.add(hit);
            }
        }
        if (groupedChildren.isEmpty()) {
            return hits;
        }

        Map<String, SearchResponse.SearchHit> parents = queryChunksById(groupedChildren.keySet());
        List<SearchResponse.SearchHit> aggregated = new ArrayList<>(standalone);
        for (Map.Entry<String, List<SearchResponse.SearchHit>> entry : groupedChildren.entrySet()) {
            List<SearchResponse.SearchHit> children = entry.getValue();
            SearchResponse.SearchHit best = children.stream()
                    .max(Comparator.comparingDouble(SearchResponse.SearchHit::getVectorScore)).orElseThrow();
            SearchResponse.SearchHit parent = parents.get(entry.getKey());
            if (parent == null) {
                log.warn("Parent chunk {} not found; returning best child", entry.getKey());
                best.setMatchedChunks(children.stream().map(RagService::toMatchedChunk).toList());
                aggregated.add(best);
                continue;
            }
            parent.setScore(best.getVectorScore());
            parent.setVectorScore(best.getVectorScore());
            parent.setParentId(parent.getId());
            parent.setMatchedChunks(children.stream()
                    .sorted(Comparator.comparingDouble(SearchResponse.SearchHit::getVectorScore).reversed())
                    .map(RagService::toMatchedChunk).toList());
            aggregated.add(parent);
        }
        return aggregated.stream()
                .sorted(Comparator.comparingDouble(SearchResponse.SearchHit::getVectorScore).reversed())
                .toList();
    }

    private Map<String, SearchResponse.SearchHit> queryChunksById(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String expr = ids.stream().map(id -> "\"" + escapeExpr(id) + "\"")
                .collect(Collectors.joining(",", "id in [", "]"));
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .withOutFields(CHUNK_OUTPUT_FIELDS)
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();
        R<QueryResults> result = milvusClient.query(param);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.warn("Parent query failed: {}", result.getMessage());
            return Map.of();
        }
        Map<String, SearchResponse.SearchHit> parents = new LinkedHashMap<>();
        for (QueryResultsWrapper.RowRecord row : new QueryResultsWrapper(result.getData()).getRowRecords()) {
            Map<String, Object> values = row.getFieldValues();
            SearchResponse.SearchHit hit = hitFromRow(values);
            parents.put(hit.getId(), hit);
        }
        return parents;
    }

    /**
     * Reads persisted chunks for the administrator knowledge-base view. This path performs only
     * a scalar Milvus query; it does not invoke embedding, rerank, or chat models.
     */
    public List<SearchResponse.SearchHit> listChunksBySources(Collection<String> sources) {
        if (!collectionReady || sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<String> normalized = sources.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String expr = normalized.stream()
                .map(value -> "\"" + escapeExpr(value) + "\"")
                .collect(Collectors.joining(",", "source in [", "]"));
        return queryAdminChunks(expr, CHUNK_OUTPUT_FIELDS);
    }

    public List<SearchResponse.SearchHit> listAllChunks() {
        if (!collectionReady) {
            return List.of();
        }
        return queryAdminChunks("id != \"\"", CATALOG_OUTPUT_FIELDS);
    }

    public List<SearchResponse.SearchHit> listChunksBySource(String source) {
        return source == null ? List.of() : listChunksBySources(List.of(source));
    }

    private List<SearchResponse.SearchHit> queryAdminChunks(String expr, List<String> outputFields) {
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .withOutFields(outputFields)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withLimit(MAX_ADMIN_CHUNK_ROWS)
                .build();
        R<QueryResults> result = milvusClient.query(param);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Failed to load knowledge chunks: " + result.getMessage());
        }
        List<QueryResultsWrapper.RowRecord> rows =
                new QueryResultsWrapper(result.getData()).getRowRecords();
        if (rows.size() >= MAX_ADMIN_CHUNK_ROWS) {
            throw new IllegalStateException("Knowledge chunk query reached the administrative row limit");
        }
        return rows.stream()
                .map(row -> hitFromRow(row.getFieldValues()))
                .sorted(Comparator
                        .comparing((SearchResponse.SearchHit hit) -> hit.getMetadata().getSource())
                        .thenComparingInt(SearchResponse.SearchHit::getChunkIndex))
                .toList();
    }

    private static SearchResponse.SearchHit hitFromRow(Map<String, Object> values) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setSource(asString(values.get("source")));
        metadata.setDepartment(asString(values.get("department")));
        metadata.setRole(asString(values.get("role")));
        metadata.setVersion(asString(values.get("version")));
        metadata.setCreateTime(asString(values.get("create_time")));
        SearchResponse.SearchHit hit = new SearchResponse.SearchHit(asString(values.get("id")),
                asString(values.get("content")), 0.0, 0.0, null, metadata);
        hit.setDocumentId(asString(values.get("document_id")));
        hit.setParentId(asString(values.get("parent_id")));
        hit.setChunkLevel(asString(values.get("chunk_level")));
        hit.setChunkIndex(asInt(values.get("chunk_index")));
        hit.setTotalChunks(asInt(values.get("total_chunks")));
        hit.setStrategy(asString(values.get("strategy")));
        hit.setContentType(asString(values.get("content_type")));
        hit.setTitlePath(asString(values.get("title_path")));
        hit.setStartOffset(asLong(values.get("start_offset")));
        hit.setEndOffset(asLong(values.get("end_offset")));
        return hit;
    }

    private static SearchResponse.MatchedChunk toMatchedChunk(SearchResponse.SearchHit hit) {
        return new SearchResponse.MatchedChunk(hit.getId(), hit.getContent(),
                hit.getVectorScore() == null ? hit.getScore() : hit.getVectorScore(),
                hit.getChunkIndex(), hit.getStartOffset(), hit.getEndOffset());
    }

    // ==================== 工具方法 ====================

    /**
     * 构建 Milvus 标量过滤表达式。各过滤条件之间为 AND 关系。
     */
    public static String buildFilterExpression(String source, String department,
                                                String role, String version) {
        return buildFilterExpression(source, department, role, version, null, null);
    }

    public static String buildFilterExpression(String source, String department,
                                                String role, String version,
                                                List<String> departments, List<String> roles) {
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
        List<String> access = new ArrayList<>();
        if (roles != null && !roles.isEmpty()) {
            access.add("role in [" + roles.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "\"" + escapeExpr(value) + "\"")
                    .collect(java.util.stream.Collectors.joining(", ")) + "]");
        }
        if (departments != null && !departments.isEmpty()) {
            access.add("department in [" + departments.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "\"" + escapeExpr(value) + "\"")
                    .collect(java.util.stream.Collectors.joining(", ")) + "]");
        }
        if (!access.isEmpty()) {
            parts.add("(" + String.join(" || ", access) + ")");
        }
        return parts.isEmpty() ? null : String.join(" && ", parts);
    }

    /** 转义 Milvus 表达式中的特殊字符 */
    private static String escapeExpr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<Float> generateEmbedding(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != dimensions) {
            throw new IllegalStateException("Embedding dimension " + vector.length
                    + " does not match configured Milvus dimension " + dimensions);
        }
        List<Float> result = new ArrayList<>(vector.length);
        for (float v : vector) {
            result.add(v);
        }
        return result;
    }

    private String nn(String value) {
        return value != null ? value : "";
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private static long longMetadata(Map<String, Object> metadata, String key) {
        return asLong(metadata.get(key));
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private static int asInt(Object value) {
        long parsed = asLong(value);
        return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
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
                "collection", collectionName,
                "service", "Milvus RAG Service (custom schema)"
        );
    }
}
