package com.example.mallorderobservability.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Trace 事件写入 Elasticsearch，并支持按 traceId 查询。
 */
public class ElasticsearchTraceRepository {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTraceRepository.class);

    private final ElasticsearchClient client;
    private final ObservabilityProperties properties;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public ElasticsearchTraceRepository(ElasticsearchClient client, ObservabilityProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 确保索引存在后，以事件编号为文档编号保存单个 Trace 事件。
     *
     * @param event Trace 事件
     * @throws IOException Elasticsearch 通信失败时抛出
     */
    public void save(TraceEvent event) throws IOException {
        ensureIndex();
        client.index(IndexRequest.of(i -> i
                .index(properties.getElasticsearch().getIndex())
                .id(event.getEventId())
                .document(toDocument(event))));
    }

    /**
     * 查询同一 Trace 的最多 200 个事件，并按毫秒时间戳升序排列。
     *
     * @param traceId Trace 编号
     * @return 按发生顺序排列的事件文档
     * @throws IOException Elasticsearch 通信失败时抛出
     */
    public List<Map<String, Object>> findByTraceId(String traceId) throws IOException {
        ensureIndex();
        SearchResponse<Map> response = client.search(SearchRequest.of(s -> s
                        .index(properties.getElasticsearch().getIndex())
                        .size(200)
                        .query(q -> q.term(t -> t.field("traceId").value(traceId)))),
                Map.class);

        List<Map<String, Object>> events = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                events.add(new LinkedHashMap<>(hit.source()));
            }
        }
        events.sort((a, b) -> Long.compare(
                ((Number) a.getOrDefault("timestampMs", 0L)).longValue(),
                ((Number) b.getOrDefault("timestampMs", 0L)).longValue()));
        return events;
    }

    private void ensureIndex() throws IOException {
        if (indexReady.get()) {
            return;
        }
        synchronized (indexReady) {
            if (indexReady.get()) {
                return;
            }
            String index = properties.getElasticsearch().getIndex();
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(c -> c
                        .index(index)
                        .mappings(TypeMapping.of(m -> m
                                .properties("traceId", Property.of(p -> p.keyword(k -> k)))
                                .properties("spanId", Property.of(p -> p.keyword(k -> k)))
                                .properties("parentSpanId", Property.of(p -> p.keyword(k -> k)))
                                .properties("eventType", Property.of(p -> p.keyword(k -> k)))
                                .properties("operation", Property.of(p -> p.keyword(k -> k)))
                                .properties("serviceName", Property.of(p -> p.keyword(k -> k)))
                                .properties("status", Property.of(p -> p.keyword(k -> k)))
                                .properties("timestampMs", Property.of(p -> p.long_(l -> l)))
                                .properties("durationMs", Property.of(p -> p.long_(l -> l)))
                                .properties("errorMessage", Property.of(p -> p.text(t -> t)))
                                .properties("spanKind", Property.of(p -> p.keyword(k -> k)))
                                .properties("model", Property.of(p -> p.keyword(k -> k)))
                                .properties("finishReason", Property.of(p -> p.keyword(k -> k)))
                                .properties("inputToken", Property.of(p -> p.long_(l -> l)))
                                .properties("outputToken", Property.of(p -> p.long_(l -> l)))
                                .properties("contextChunks", Property.of(p -> p.long_(l -> l)))
                                .properties("answerLength", Property.of(p -> p.long_(l -> l)))
                                .properties("outputLength", Property.of(p -> p.long_(l -> l)))
                                .properties("streaming", Property.of(p -> p.boolean_(b -> b)))
                                .properties("firstTokenLatencyMs", Property.of(p -> p.long_(l -> l)))
                                .properties("promptVersion", Property.of(p -> p.keyword(k -> k)))
                                .properties("promptLength", Property.of(p -> p.long_(l -> l)))
                                .properties("chunkCount", Property.of(p -> p.long_(l -> l)))
                                .properties("historyCount", Property.of(p -> p.long_(l -> l)))
                                .properties("memoryCount", Property.of(p -> p.long_(l -> l)))
                                // Agent / 多租户 / 对话关联
                                .properties("agentName", Property.of(p -> p.keyword(k -> k)))
                                .properties("agentVersion", Property.of(p -> p.keyword(k -> k)))
                                .properties("conversationId", Property.of(p -> p.keyword(k -> k)))
                                .properties("tenantId", Property.of(p -> p.keyword(k -> k)))
                                // 时延与检索质量
                                .properties("ttftMs", Property.of(p -> p.long_(l -> l)))
                                .properties("ragScore", Property.of(p -> p.float_(f -> f)))
                                // Tool 调用详情（nested）
                                .properties("toolCalls", Property.of(p -> p.nested(n -> n
                                        .properties("toolName", Property.of(tp -> tp.keyword(k -> k)))
                                        .properties("status", Property.of(tp -> tp.keyword(k -> k)))
                                        .properties("errorCode", Property.of(tp -> tp.integer(i -> i)))
                                        .properties("durationMs", Property.of(tp -> tp.long_(l -> l))))))
                                .properties("attributes", Property.of(p -> p.object(o -> o.enabled(true))))))));
                log.info("Created Elasticsearch index: {}", index);
            }
            indexReady.set(true);
        }
    }

    Map<String, Object> toDocument(TraceEvent event) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", event.getEventId());
        doc.put("traceId", event.getTraceId());
        doc.put("spanId", event.getSpanId());
        doc.put("parentSpanId", event.getParentSpanId());
        doc.put("eventType", event.getEventType() != null ? event.getEventType().name() : null);
        doc.put("operation", event.getOperation());
        doc.put("serviceName", event.getServiceName());
        doc.put("status", event.getStatus());
        doc.put("timestampMs", event.getTimestampMs());
        doc.put("timestamp", event.getTimestamp());
        doc.put("durationMs", event.getDurationMs());
        doc.put("errorMessage", TracePrivacy.sanitizeErrorLabel(event.getErrorMessage()));

        Map<String, Object> attributes = TracePrivacy.sanitizeAttributes(event.getAttributes());
        promoteCommonAgentFields(doc, attributes);
        if (RagTraceService.LLM_OPERATION.equals(event.getOperation())
                && event.getEventType() == TraceEventType.SPAN_END) {
            enrichLlmDocument(doc, attributes);
        } else if (RagTraceService.PROMPT_BUILD_OPERATION.equals(event.getOperation())
                && event.getEventType() == TraceEventType.SPAN_START) {
            enrichPromptBuildDocument(doc, attributes);
        } else {
            doc.put("attributes", attributes);
        }
        return doc;
    }

    /**
     * 将 Agent / 多租户 / 时延 / Tool 等通用字段从 attributes 提升到文档顶层，便于 ES 检索与聚合。
     */
    private void promoteCommonAgentFields(Map<String, Object> doc, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        copyIfPresent(doc, attributes, "agentName");
        copyIfPresent(doc, attributes, "agentVersion");
        copyIfPresent(doc, attributes, "conversationId");
        copyIfPresent(doc, attributes, "tenantId");
        copyIfPresent(doc, attributes, "ttftMs");
        copyIfPresent(doc, attributes, "ragScore");
        copyIfPresent(doc, attributes, "toolCalls");
    }

    /**
     * prompt_build SPAN_START：关键字段提升到顶层。
     */
    private void enrichPromptBuildDocument(Map<String, Object> doc, Map<String, Object> attributes) {
        doc.put("spanKind", "prompt");
        if (attributes == null || attributes.isEmpty()) {
            doc.put("attributes", Map.of());
            return;
        }

        copyIfPresent(doc, attributes, "promptVersion");
        copyIfPresent(doc, attributes, "promptLength");
        copyIfPresent(doc, attributes, "chunkCount");
        copyIfPresent(doc, attributes, "historyCount");
        copyIfPresent(doc, attributes, "memoryCount");

        Map<String, Object> slimAttributes = new LinkedHashMap<>();
        for (String key : List.of(
                "promptVersion", "promptLength", "chunkCount",
                "historyCount", "memoryCount", "durationMs", "startTimestampMs")) {
            if (attributes.containsKey(key)) {
                slimAttributes.put(key, attributes.get(key));
            }
        }
        doc.put("attributes", slimAttributes);
    }

    /**
     * llm 单条 SPAN_END：关键字段提升到顶层，attributes 仅保留指标类字段。
     */
    private void enrichLlmDocument(Map<String, Object> doc, Map<String, Object> attributes) {
        doc.put("spanKind", "llm");
        if (attributes == null || attributes.isEmpty()) {
            doc.put("attributes", Map.of());
            return;
        }

        copyIfPresent(doc, attributes, "model");
        copyIfPresent(doc, attributes, "finishReason");
        copyIfPresent(doc, attributes, "inputToken");
        copyIfPresent(doc, attributes, "outputToken");
        copyIfPresent(doc, attributes, "contextChunks");
        copyIfPresent(doc, attributes, "outputLength");
        copyIfPresent(doc, attributes, "temperature");
        copyIfPresent(doc, attributes, "streaming");
        copyIfPresent(doc, attributes, "ttftMs");
        copyIfPresent(doc, attributes, "firstTokenLatencyMs");
        copyIfPresent(doc, attributes, "chunkCount");

        Map<String, Object> slimAttributes = new LinkedHashMap<>();
        for (String key : List.of(
                "model", "temperature", "contextChunks",
                "inputToken", "outputToken", "finishReason", "outputLength",
                "streaming", "ttftMs", "firstTokenLatencyMs", "chunkCount",
                "durationMs", "startTimestampMs")) {
            if (attributes.containsKey(key)) {
                slimAttributes.put(key, attributes.get(key));
            }
        }
        doc.put("attributes", slimAttributes);
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
