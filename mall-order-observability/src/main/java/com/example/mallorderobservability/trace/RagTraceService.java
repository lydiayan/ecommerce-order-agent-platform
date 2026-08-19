package com.example.mallorderobservability.trace;

import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.model.TraceEventType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG Trace 采集：创建 trace/span 并通过 {@link TracePublisher} 投递。
 */
public class RagTraceService {

    public static final String LLM_OPERATION = "llm";
    public static final String PROMPT_BUILD_OPERATION = "prompt_build";

    private final TracePublisher tracePublisher;
    private final ObservabilityProperties properties;
    private final ThreadLocal<Deque<SpanContext>> spanStack = ThreadLocal.withInitial(ArrayDeque::new);

    public RagTraceService(TracePublisher tracePublisher, ObservabilityProperties properties) {
        this.tracePublisher = tracePublisher;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public RagTraceScope begin(String operation) {
        return begin(operation, Map.of());
    }

    public RagTraceScope begin(String operation, Map<String, Object> attributes) {
        if (!properties.isEnabled()) {
            return RagTraceScope.noop();
        }

        Deque<SpanContext> stack = spanStack.get();
        stack.clear();

        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString();

        TraceEvent traceStart = TraceEvent.create(
                TraceEventType.TRACE_START, traceId, spanId, null, operation, properties.getServiceName());
        traceStart.getAttributes().putAll(safeAttributes(attributes));
        tracePublisher.publish(traceStart);

        TraceEvent spanStart = TraceEvent.create(
                TraceEventType.SPAN_START, traceId, spanId, null, operation, properties.getServiceName());
        spanStart.getAttributes().putAll(safeAttributes(attributes));
        tracePublisher.publish(spanStart);

        SpanContext context = new SpanContext(traceId, spanId, null, System.currentTimeMillis(), true);
        stack.push(context);
        return new RagTraceScope(this, context, operation, safeAttributes(attributes));
    }

    public RagTraceScope childSpan(String operation, Map<String, Object> attributes) {
        Deque<SpanContext> stack = spanStack.get();
        SpanContext parent = stack.peek();
        if (parent == null) {
            return begin(operation, attributes);
        }
        return childSpan(parent, operation, attributes);
    }

    /**
     * 在指定父 span 下创建子 span（不依赖 ThreadLocal 栈顶，供跨 Advisor 线程使用）。
     */
    public RagTraceScope childSpan(SpanContext parent, String operation, Map<String, Object> attributes) {
        if (parent == null) {
            return childSpan(operation, attributes);
        }

        String spanId = UUID.randomUUID().toString();
        boolean llmSpan = LLM_OPERATION.equals(operation);
        Map<String, Object> startAttributes = safeAttributes(attributes);
        if (!llmSpan) {
            TraceEvent spanStart = TraceEvent.create(
                    TraceEventType.SPAN_START,
                    parent.traceId(),
                    spanId,
                    parent.spanId(),
                    operation,
                    properties.getServiceName());
            spanStart.getAttributes().putAll(startAttributes);
            tracePublisher.publish(spanStart);
        }

        SpanContext child = new SpanContext(parent.traceId(), spanId, parent.spanId(),
                System.currentTimeMillis(), false);
        Deque<SpanContext> stack = spanStack.get();
        stack.push(child);
        return new RagTraceScope(this, child, operation, llmSpan ? startAttributes : Map.of());
    }

    public RagTraceScope childSpan(String operation) {
        return childSpan(operation, Map.of());
    }

    void endSpan(SpanContext context, String operation, String status,
                 Map<String, Object> attributes, String errorMessage) {
        Deque<SpanContext> stack = spanStack.get();
        long durationMs = System.currentTimeMillis() - context.startMs();

        TraceEvent spanEnd = TraceEvent.create(
                TraceEventType.SPAN_END,
                context.traceId(),
                context.spanId(),
                context.parentSpanId(),
                operation,
                properties.getServiceName());
        spanEnd.setStatus(status);
        spanEnd.setDurationMs(durationMs);
        spanEnd.setErrorMessage(errorMessage);
        Map<String, Object> mergedAttributes = safeAttributes(attributes);
        mergedAttributes.put("durationMs", durationMs);
        mergedAttributes.put("startTimestampMs", context.startMs());
        spanEnd.getAttributes().putAll(mergedAttributes);
        tracePublisher.publish(spanEnd);

        if (!stack.isEmpty() && stack.peek() == context) {
            stack.pop();
        }

        if (context.root()) {
            TraceEvent traceEnd = TraceEvent.create(
                    TraceEventType.TRACE_END,
                    context.traceId(),
                    context.spanId(),
                    null,
                    operation,
                    properties.getServiceName());
            traceEnd.setStatus(status);
            traceEnd.setDurationMs(durationMs);
            traceEnd.setErrorMessage(errorMessage);
            traceEnd.getAttributes().putAll(safeAttributes(attributes));
            tracePublisher.publish(traceEnd);
            stack.clear();
            spanStack.remove();
        }
    }

    public String currentTraceId() {
        Deque<SpanContext> stack = spanStack.get();
        SpanContext ctx = stack.peek();
        return ctx != null ? ctx.traceId() : null;
    }

    private static Map<String, Object> safeAttributes(Map<String, Object> attributes) {
        return TracePrivacy.sanitizeAttributes(attributes);
    }

    record SpanContext(String traceId, String spanId, String parentSpanId, long startMs, boolean root) {
    }
}
