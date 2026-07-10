package com.example.mallorderobservability.trace;

import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RagTraceServiceTest {

    @Test
    void beginEmitsTraceAndSpanEvents() {
        List<String> operations = new ArrayList<>();
        TracePublisher publisher = event -> operations.add(event.getEventType() + ":" + event.getOperation());

        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setServiceName("test-service");

        RagTraceService service = new RagTraceService(publisher, properties);

        try (RagTraceScope scope = service.begin("rag.search", Map.of("query", "婚假"))) {
            assertNotNull(scope.traceId());
            try (RagTraceScope child = scope.child("embed")) {
                child.attribute("dimension", 1536);
            }
            scope.attribute("hitCount", 3);
        }

        assertEquals(6, operations.size());
        assertEquals("TRACE_START:rag.search", operations.get(0));
        assertEquals("SPAN_START:rag.search", operations.get(1));
        assertEquals("SPAN_START:embed", operations.get(2));
        assertEquals("SPAN_END:embed", operations.get(3));
        assertEquals("SPAN_END:rag.search", operations.get(4));
        assertEquals("TRACE_END:rag.search", operations.get(5));
    }

    @Test
    void llmGenerateEmitsSingleSpanEndWithMergedAttributes() {
        List<String> operations = new ArrayList<>();
        TracePublisher publisher = event -> operations.add(event.getEventType() + ":" + event.getOperation());

        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.setServiceName("test-service");

        RagTraceService service = new RagTraceService(publisher, properties);

        try (RagTraceScope root = service.begin("rag.ask", Map.of("query", "test"))) {
            try (RagTraceScope llm = root.child(RagTraceService.LLM_OPERATION, Map.of("model", "qwen-plus", "userQuery", "hello"))) {
                llm.attribute("inputToken", 10);
                llm.attribute("outputToken", 5);
            }
        }

        assertEquals(5, operations.size());
        assertEquals("TRACE_START:rag.ask", operations.get(0));
        assertEquals("SPAN_START:rag.ask", operations.get(1));
        assertEquals("SPAN_END:llm", operations.get(2));
        assertEquals("SPAN_END:rag.ask", operations.get(3));
        assertEquals("TRACE_END:rag.ask", operations.get(4));
    }
}
