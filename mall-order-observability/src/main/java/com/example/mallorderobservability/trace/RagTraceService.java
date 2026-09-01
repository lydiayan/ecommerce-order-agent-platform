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

    /** @return 当前是否启用 Trace 事件采集 */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 为操作创建新的根 Trace，不附加初始属性。
     *
     * @param operation 根操作名称
     * @return 可用于 try-with-resources 的作用域；未启用时返回空作用域
     */
    public RagTraceScope begin(String operation) {
        return begin(operation, Map.of());
    }

    /**
     * 清空当前线程旧上下文并创建根 Trace，同时发布 TRACE_START 和 SPAN_START。
     * 属性在发布前会移除原始业务和模型载荷。
     *
     * @param operation 根操作名称
     * @param attributes 初始非敏感属性
     * @return 根 Trace 作用域
     */
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

    /**
     * 在当前线程栈顶 Span 下创建子 Span；没有父上下文时自动创建根 Trace。
     *
     * @param operation 子操作名称
     * @param attributes 初始非敏感属性
     * @return 子 Span 或新的根 Trace 作用域
     */
    public RagTraceScope childSpan(String operation, Map<String, Object> attributes) {
        Deque<SpanContext> stack = spanStack.get();
        SpanContext parent = stack.peek();
        if (parent == null) {
            return begin(operation, attributes);
        }
        return childSpan(parent, operation, attributes);
    }

    /**
     * 在指定父 Span 下创建子 Span，不依赖 ThreadLocal 栈顶，供跨 Advisor 线程传播上下文。
     * LLM Span 的开始属性会延迟合并到结束事件，避免重复的模型事件。
     *
     * @param parent 显式父 Span 上下文
     * @param operation 子操作名称
     * @param attributes 初始非敏感属性
     * @return 子 Span 作用域
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

    /**
     * 在当前 Span 下创建无初始属性的子 Span。
     *
     * @param operation 子操作名称
     * @return 子 Span 作用域
     */
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

    /**
     * 读取当前线程 Span 栈顶所属的 Trace 编号。
     *
     * @return 当前 Trace 编号；没有活动 Span 时返回 {@code null}
     */
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
