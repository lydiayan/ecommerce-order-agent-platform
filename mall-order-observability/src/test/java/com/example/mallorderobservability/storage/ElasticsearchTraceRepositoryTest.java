package com.example.mallorderobservability.storage;

import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;
import com.example.mallorderobservability.trace.RagTraceService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticsearchTraceRepositoryTest {

    @Test
    void keepsStreamingMetricsOnLlmSpanEnd() {
        ElasticsearchTraceRepository repository = new ElasticsearchTraceRepository(
                null, new ObservabilityProperties());
        TraceEvent event = TraceEvent.create(
                TraceEventType.SPAN_END, "trace-1", "span-1", "parent-1",
                RagTraceService.LLM_OPERATION, "test-agent");
        event.setAttributes(Map.of(
                "model", "qwen-plus",
                "streaming", true,
                "ttftMs", 321L,
                "firstTokenLatencyMs", 321L,
                "chunkCount", 18L,
                "outputLength", 185));

        Map<String, Object> document = repository.toDocument(event);

        assertEquals(true, document.get("streaming"));
        assertEquals(321L, document.get("ttftMs"));
        assertEquals(321L, document.get("firstTokenLatencyMs"));
        assertEquals(18L, document.get("chunkCount"));
        assertEquals(185, document.get("outputLength"));
        assertEquals(true, ((Map<?, ?>) document.get("attributes")).get("streaming"));
        assertEquals(321L, ((Map<?, ?>) document.get("attributes")).get("ttftMs"));
    }
}
