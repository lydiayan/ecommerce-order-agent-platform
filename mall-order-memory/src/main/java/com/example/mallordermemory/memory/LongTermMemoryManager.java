package com.example.mallordermemory.memory;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.*;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.response.SearchResultsWrapper.IDScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆管理器
 * <p>
 * 基于 Milvus 低层 SDK（MilvusServiceClient）实现长期记忆存储。
 * 每种记忆类型（USER_PROFILE / FACT / SUMMARY）对应一个独立的 Milvus 集合，
 * 支持向量的增、查（语义检索）、删、集合自动初始化。
 * </p>
 */
public class LongTermMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryManager.class);

    /** 向量维度，需与 embedding 模型输出维度一致（text-embedding-v2 = 1536） */
    private final int dimension;

    private final MilvusServiceClient milvusClient;
    private final Map<MemoryType, Boolean> collectionReady = new HashMap<>();
    private volatile boolean initialized;

    private static final MemoryType[] STORED_MEMORY_TYPES = {MemoryType.FACT, MemoryType.SUMMARY};

    public LongTermMemoryManager(MilvusServiceClient milvusClient, int dimension) {
        this.milvusClient = milvusClient;
        this.dimension = dimension;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            initCollections();
            initialized = true;
        }
    }

    // ==================== 集合初始化 ====================

    /**
     * 为每种记忆类型创建对应的 Milvus 集合（若不存在）
     */
    private void initCollections() {
        for (MemoryType type : STORED_MEMORY_TYPES) {
            ensureCollection(type);
        }
        log.info("Long-term memory collections initialized (FACT/SUMMARY only, USER_PROFILE -> MySQL)");
    }

    private void ensureCollection(MemoryType type) {
        String collectionName = type.collectionName();

        // 检查集合是否存在
        DescribeCollectionParam describeParam = DescribeCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        R<DescribeCollectionResponse> describeResult = milvusClient.describeCollection(describeParam);

        if (describeResult.getStatus() == R.Status.Success.getCode()) {
            log.info("Collection already exists: {}", collectionName);
            loadCollection(collectionName);
            collectionReady.put(type, true);
            return;
        }

        // 创建集合
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .build();

        FieldType userIdField = FieldType.newBuilder()
                .withName("user_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType conversationIdField = FieldType.newBuilder()
                .withName("conversation_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();

        FieldType importanceField = FieldType.newBuilder()
                .withName("importance")
                .withDataType(DataType.Float)
                .build();

        FieldType createdField = FieldType.newBuilder()
                .withName("created_at")
                .withDataType(DataType.VarChar)
                .withMaxLength(32)
                .build();

        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.VarChar)
                .withMaxLength(4096)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Memory collection: " + type.getDisplayName())
                .addFieldType(idField)
                .addFieldType(userIdField)
                .addFieldType(conversationIdField)
                .addFieldType(contentField)
                .addFieldType(embeddingField)
                .addFieldType(importanceField)
                .addFieldType(createdField)
                .addFieldType(metadataField)
                .build();

        R<RpcStatus> createResult = milvusClient.createCollection(createParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            log.error("Failed to create collection {}: {}", collectionName, createResult.getMessage());
            collectionReady.put(type, false);
            return;
        }
        log.info("Created collection: {}", collectionName);

        // 创建索引（IVF_FLAT, COSINE）
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build();

        R<RpcStatus> indexResult = milvusClient.createIndex(indexParam);
        if (indexResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Failed to create index for {}: {}", collectionName, indexResult.getMessage());
        }

        // 加载集合到内存
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        milvusClient.loadCollection(loadParam);

        collectionReady.put(type, true);
        log.info("Collection {} initialized and loaded", collectionName);
    }

    private void loadCollection(String collectionName) {
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        R<RpcStatus> loadResult = milvusClient.loadCollection(loadParam);
        if (loadResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Failed to load collection {}: {}", collectionName, loadResult.getMessage());
        }
    }

    // ==================== 写操作 ====================

    /**
     * 存储一条记忆到 Milvus（包含 embedding 向量）
     *
     * @param entry 记忆条目（需已设置 embedding）
     */
    public boolean store(MemoryEntry entry) {
        ensureInitialized();
        MemoryType type = entry.getType();
        if (type == MemoryType.USER_PROFILE) {
            log.warn("USER_PROFILE is stored in MySQL, skip Milvus store for userId={}", entry.getUserId());
            return false;
        }
        if (!isReady(type)) {
            log.warn("Collection {} is not ready, skipping store", type.collectionName());
            return false;
        }

        if (entry.getEmbedding() == null || entry.getEmbedding().length == 0) {
            log.warn("Skipping store for entry {}: embedding is empty", entry.getId());
            return false;
        }

        if (existsById(type, entry.getId())) {
            log.debug("Skip duplicate memory entry id={} in {}", entry.getId(), type.collectionName());
            return false;
        }

        String collectionName = type.collectionName();

        List<String> ids = List.of(entry.getId());
        List<String> userIds = List.of(entry.getUserId() != null ? entry.getUserId() : "");
        List<String> convIds = List.of(entry.getConversationId() != null ? entry.getConversationId() : "");
        List<String> contents = List.of(entry.getContent() != null ? entry.getContent() : "");
        List<List<Float>> embeddings = List.of(toFloatList(entry.getEmbedding()));
        List<Float> importances = List.of((float) entry.getImportance());
        List<String> createdAts = List.of(entry.getCreatedAt() != null
                ? entry.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        List<String> metadatas = List.of(entry.getMetadata() != null ? entry.getMetadata().toString() : "{}");

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(List.of(
                        new InsertParam.Field("id", ids),
                        new InsertParam.Field("user_id", userIds),
                        new InsertParam.Field("conversation_id", convIds),
                        new InsertParam.Field("content", contents),
                        new InsertParam.Field("embedding", embeddings),
                        new InsertParam.Field("importance", importances),
                        new InsertParam.Field("created_at", createdAts),
                        new InsertParam.Field("metadata", metadatas)
                ))
                .build();

        R<MutationResult> result = milvusClient.insert(insertParam);
        if (result.getStatus() == R.Status.Success.getCode()) {
            log.debug("Stored memory entry {} in collection {}", entry.getId(), collectionName);
            return true;
        }
        log.error("Failed to store memory entry {} in {}: {}", entry.getId(), collectionName, result.getMessage());
        return false;
    }

    /**
     * 批量存储记忆条目
     *
     * @param entries 记忆条目列表
     */
    public void storeBatch(List<MemoryEntry> entries) {
        ensureInitialized();
        Map<MemoryType, List<MemoryEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(MemoryEntry::getType));

        for (Map.Entry<MemoryType, List<MemoryEntry>> group : grouped.entrySet()) {
            MemoryType type = group.getKey();
            List<MemoryEntry> batch = group.getValue();
            String collectionName = type.collectionName();

            if (!isReady(type)) continue;

            List<String> ids = new ArrayList<>();
            List<String> userIds = new ArrayList<>();
            List<String> convIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> embeddings = new ArrayList<>();
            List<Float> importances = new ArrayList<>();
            List<String> createdAts = new ArrayList<>();
            List<String> metadatas = new ArrayList<>();

            for (MemoryEntry entry : batch) {
                if (entry.getEmbedding() == null || entry.getEmbedding().length == 0) {
                    continue;
                }
                ids.add(entry.getId());
                userIds.add(entry.getUserId() != null ? entry.getUserId() : "");
                convIds.add(entry.getConversationId() != null ? entry.getConversationId() : "");
                contents.add(entry.getContent() != null ? entry.getContent() : "");
                embeddings.add(toFloatList(entry.getEmbedding()));
                importances.add((float) entry.getImportance());
                createdAts.add(entry.getCreatedAt() != null
                        ? entry.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                metadatas.add(entry.getMetadata() != null ? entry.getMetadata().toString() : "{}");
            }

            if (ids.isEmpty()) {
                continue;
            }

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(List.of(
                            new InsertParam.Field("id", ids),
                            new InsertParam.Field("user_id", userIds),
                            new InsertParam.Field("conversation_id", convIds),
                            new InsertParam.Field("content", contents),
                            new InsertParam.Field("embedding", embeddings),
                            new InsertParam.Field("importance", importances),
                            new InsertParam.Field("created_at", createdAts),
                            new InsertParam.Field("metadata", metadatas)
                    ))
                    .build();

            R<MutationResult> result = milvusClient.insert(insertParam);
            if (result.getStatus() == R.Status.Success.getCode()) {
                log.info("Stored {} memory entries in collection {}", batch.size(), collectionName);
            } else {
                log.error("Failed to store batch in {}: {}", collectionName, result.getMessage());
            }
        }
    }

    // ==================== 读操作 ====================

    /**
     * 语义搜索长期记忆
     *
     * @param type     记忆类型（null 表示搜索所有类型）
     * @param queryEmbedding 查询向量
     * @param topK     返回结果数量
     * @return 匹配的记忆条目列表
     */
    public List<MemoryEntry> search(MemoryType type, float[] queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        ensureInitialized();
        List<MemoryEntry> results = new ArrayList<>();

        if (type != null) {
            if (type == MemoryType.USER_PROFILE) {
                return List.of();
            }
            return searchCollection(type, queryEmbedding, topK);
        }

        int perTypeTopK = Math.max(1, topK / STORED_MEMORY_TYPES.length);
        for (MemoryType t : STORED_MEMORY_TYPES) {
            results.addAll(searchCollection(t, queryEmbedding, perTypeTopK));
        }

        // 按 score 排序
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<MemoryEntry> searchCollection(MemoryType type, float[] queryEmbedding, int topK) {
        if (!isReady(type) || topK <= 0) {
            return List.of();
        }

        String collectionName = type.collectionName();

        List<String> searchOutputFields = List.of("id", "user_id", "conversation_id", "content",
                "importance", "created_at", "metadata");

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(List.of(toFloatList(queryEmbedding)))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\":16}")
                .withOutFields(searchOutputFields)
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();

        R<SearchResults> searchResult = milvusClient.search(searchParam);
        if (searchResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Search failed on {}: {}", collectionName, searchResult.getMessage());
            return List.of();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
        List<MemoryEntry> entries = new ArrayList<>();

        try {
            // 第一个向量结果的 ID 列表
            List<String> ids = (List<String>) wrapper.getFieldData("id", 0);
            List<String> userIds = (List<String>) wrapper.getFieldData("user_id", 0);
            List<String> convIds = (List<String>) wrapper.getFieldData("conversation_id", 0);
            List<String> contents = (List<String>) wrapper.getFieldData("content", 0);
            List<Float> importances = (List<Float>) wrapper.getFieldData("importance", 0);
            List<String> createdAts = (List<String>) wrapper.getFieldData("created_at", 0);
            List<String> metadatas = (List<String>) wrapper.getFieldData("metadata", 0);
            List<IDScore> idScores = wrapper.getIDScore(0);
            Map<String, Float> scoreMap = new LinkedHashMap<>();
            if (idScores != null) {
                for (IDScore idScore : idScores) {
                    scoreMap.put(idScore.getStrID(), idScore.getScore());
                }
            }

            for (int i = 0; i < ids.size(); i++) {
                MemoryEntry entry = new MemoryEntry();
                String entryId = ids.get(i);
                entry.setId(entryId);
                entry.setType(type);
                entry.setUserId(safeGet(userIds, i, ""));
                entry.setConversationId(safeGet(convIds, i, ""));
                entry.setContent(safeGet(contents, i, ""));
                entry.setImportance(safeGet(importances, i, 0.5f));
                entry.setCreatedAt(LocalDateTime.parse(safeGet(createdAts, i, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                entry.setScore(scoreMap.getOrDefault(entryId, 0.0f));
                entries.add(entry);
            }
        } catch (Exception e) {
            log.warn("Error parsing search results from {}: {}", collectionName, e.getMessage());
        }

        return entries;
    }

    /**
     * 按用户 ID 和记忆类型查询所有记忆
     *
     * @param type   记忆类型
     * @param userId 用户 ID
     * @return 记忆条目列表
     */
    public List<MemoryEntry> findByUser(MemoryType type, String userId) {
        // NOTE: 简单的 Milvus query 需要构建布尔表达式
        // 目前返回空（Milvus 标量过滤需要 expression 查询）
        // 生产环境中可添加 Query 查询
        log.debug("findByUser called for type={}, userId={} (scalar filtering TBD)", type, userId);
        return List.of();
    }

    /**
     * 判断指定 ID 的记忆是否已存在（用于去重）。
     */
    public boolean existsById(MemoryType type, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        ensureInitialized();
        if (!isReady(type)) {
            return false;
        }

        QueryParam queryParam = QueryParam.newBuilder()
                .withCollectionName(type.collectionName())
                .withExpr("id == \"" + escapeExprString(id) + "\"")
                .withOutFields(List.of("id"))
                .withLimit(1L)
                .build();
        R<QueryResults> result = milvusClient.query(queryParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.warn("Exists check failed on {}: {}", type.collectionName(), result.getMessage());
            return false;
        }
        return hasQueryRows(result.getData());
    }

    static boolean hasQueryRows(QueryResults data) {
        if (data == null) {
            return false;
        }
        QueryResultsWrapper wrapper = new QueryResultsWrapper(data);
        List<QueryResultsWrapper.RowRecord> rows = wrapper.getRowRecords();
        return rows != null && !rows.isEmpty();
    }

    /**
     * 从集合中按 ID 删除记忆条目
     *
     * @param type 记忆类型
     * @param ids  要删除的条目 ID 列表
     */
    public void delete(MemoryType type, List<String> ids) {
        if (!isReady(type) || ids.isEmpty()) return;

        String collectionName = type.collectionName();
        String expression = "id in " + ids.stream()
                .map(id -> "'" + id.replace("'", "\\'") + "'")
                .collect(Collectors.joining(", ", "[", "]"));

        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expression)
                .build();
        R<?> deleteResult = milvusClient.delete(deleteParam);
        if (deleteResult.getStatus() != R.Status.Success.getCode()) {
            log.warn("Delete failed on {}: {}", collectionName, deleteResult.getMessage());
        } else {
            log.info("Deleted {} entries from {}", ids.size(), collectionName);
        }
    }

    // ==================== 辅助方法 ====================

    private static String escapeExprString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private boolean isReady(MemoryType type) {
        return collectionReady.getOrDefault(type, false);
    }

    private <T> T safeGet(List<T> list, int index, T defaultValue) {
        if (list != null && index < list.size()) {
            return list.get(index);
        }
        return defaultValue;
    }

    /**
     * 关闭 Milvus 连接
     */
    public void close() {
        milvusClient.close();
    }
}
