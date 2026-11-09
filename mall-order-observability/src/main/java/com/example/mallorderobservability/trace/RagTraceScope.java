package com.example.mallorderobservability.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Trace/Span 作用域，支持 try-with-resources。
 */
public class RagTraceScope implements AutoCloseable {

    private static final RagTraceScope NOOP = new RagTraceScope(null, null, "noop", Map.of());

    private final RagTraceService traceService;
    private final RagTraceService.SpanContext context;
    private final String operation;
    private final Map<String, Object> deferredStartAttributes;
    private final Map<String, Object> endAttributes = new LinkedHashMap<>();
    private String status = "OK";
    private String errorMessage;

    RagTraceScope(RagTraceService traceService, RagTraceService.SpanContext context, String operation,
                  Map<String, Object> deferredStartAttributes) {
        this.traceService = traceService;
        this.context = context;
        this.operation = operation;
        this.deferredStartAttributes = deferredStartAttributes != null ? deferredStartAttributes : Map.of();
    }

    /** @return 不发布任何事件的共享空作用域 */
    public static RagTraceScope noop() {
        return NOOP;
    }

    /**
     * 增加一个结束事件属性，空值会被忽略。
     *
     * @param key 属性名
     * @param value 属性值
     * @return 当前作用域，便于链式调用
     */
    public RagTraceScope attribute(String key, Object value) {
        if (value != null) {
            endAttributes.put(key, value);
        }
        return this;
    }

    /**
     * 批量增加结束事件属性。
     *
     * @param attrs 属性集合
     * @return 当前作用域
     */
    public RagTraceScope attributes(Map<String, Object> attrs) {
        if (attrs != null) {
            endAttributes.putAll(attrs);
        }
        return this;
    }

    /**
     * 将作用域标记为失败，只保留异常类型而不记录任意异常文本。
     *
     * @param t 原始异常
     * @return 当前作用域
     */
    public RagTraceScope error(Throwable t) {
        this.status = "ERROR";
        this.errorMessage = t != null ? t.getClass().getSimpleName() : "UnknownError";
        return this;
    }

    /**
     * 在当前作用域下创建无初始属性的子 Span。
     *
     * @param operation 子操作名称
     * @return 子 Span；当前为空作用域时仍返回空作用域
     */
    public RagTraceScope child(String operation) {
        return child(operation, Map.of());
    }

    /**
     * 在当前显式上下文下创建子 Span，支持跨线程调用方保留正确父子关系。
     *
     * @param operation 子操作名称
     * @param attributes 初始属性
     * @return 子 Span
     */
    public RagTraceScope child(String operation, Map<String, Object> attributes) {
        if (traceService == null || context == null) {
            return NOOP;
        }
        return traceService.childSpan(context, operation, attributes);
    }

    /** @return 当前 Trace 编号；空作用域返回 {@code null} */
    public String traceId() {
        return context != null ? context.traceId() : null;
    }

    /**
     * 在作用域内执行任务，自动标记运行时异常并最终关闭 Span。
     *
     * @param supplier 待执行任务
     * @param <T> 返回值类型
     * @return 任务结果
     */
    public <T> T run(Supplier<T> supplier) {
        try {
            T result = supplier.get();
            return result;
        } catch (RuntimeException e) {
            error(e);
            throw e;
        } finally {
            close();
        }
    }

    /**
     * 合并延迟属性和结束属性，发布 SPAN_END；根作用域还会发布 TRACE_END。
     */
    @Override
    public void close() {
        if (traceService != null && context != null) {
            Map<String, Object> merged = new LinkedHashMap<>(deferredStartAttributes);
            merged.putAll(endAttributes);
            traceService.endSpan(context, operation, status, merged, errorMessage);
        }
    }
}
