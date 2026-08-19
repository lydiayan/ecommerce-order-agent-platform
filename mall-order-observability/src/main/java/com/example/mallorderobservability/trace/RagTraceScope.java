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

    public static RagTraceScope noop() {
        return NOOP;
    }

    public RagTraceScope attribute(String key, Object value) {
        if (value != null) {
            endAttributes.put(key, value);
        }
        return this;
    }

    public RagTraceScope attributes(Map<String, Object> attrs) {
        if (attrs != null) {
            endAttributes.putAll(attrs);
        }
        return this;
    }

    public RagTraceScope error(Throwable t) {
        this.status = "ERROR";
        this.errorMessage = t != null ? t.getClass().getSimpleName() : "UnknownError";
        return this;
    }

    public RagTraceScope child(String operation) {
        return child(operation, Map.of());
    }

    public RagTraceScope child(String operation, Map<String, Object> attributes) {
        if (traceService == null || context == null) {
            return NOOP;
        }
        return traceService.childSpan(context, operation, attributes);
    }

    public String traceId() {
        return context != null ? context.traceId() : null;
    }

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

    @Override
    public void close() {
        if (traceService != null && context != null) {
            Map<String, Object> merged = new LinkedHashMap<>(deferredStartAttributes);
            merged.putAll(endAttributes);
            traceService.endSpan(context, operation, status, merged, errorMessage);
        }
    }
}
