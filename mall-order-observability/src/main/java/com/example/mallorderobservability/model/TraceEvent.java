package com.example.mallorderobservability.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG Trace 事件，经 RocketMQ 异步投递后在 ES 中索引。
 */
public class TraceEvent {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private String eventId;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private TraceEventType eventType;
    private String operation;
    private String serviceName;
    private String status;
    private long timestampMs;
    /** 可读时间戳，格式 yyyy-MM-dd HH:mm:ss.SSS */
    private String timestamp;
    private Long durationMs;
    private Map<String, Object> attributes = new LinkedHashMap<>();
    private String errorMessage;

    public static TraceEvent create(TraceEventType eventType,
                                    String traceId,
                                    String spanId,
                                    String parentSpanId,
                                    String operation,
                                    String serviceName) {
        TraceEvent event = new TraceEvent();
        event.eventId = UUID.randomUUID().toString();
        event.eventType = eventType;
        event.traceId = traceId;
        event.spanId = spanId;
        event.parentSpanId = parentSpanId;
        event.operation = operation;
        event.serviceName = serviceName;
        event.status = "OK";
        event.timestampMs = System.currentTimeMillis();
        event.timestamp = TIMESTAMP_FMT.format(Instant.ofEpochMilli(event.timestampMs));
        return event;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public TraceEventType getEventType() {
        return eventType;
    }

    public void setEventType(TraceEventType eventType) {
        this.eventType = eventType;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new LinkedHashMap<>();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
