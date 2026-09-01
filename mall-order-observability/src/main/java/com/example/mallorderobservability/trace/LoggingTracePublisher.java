package com.example.mallorderobservability.trace;

import com.example.mallorderobservability.model.TraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地日志兜底，MQ 不可用时仍可观测。
 */
public class LoggingTracePublisher implements TracePublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingTracePublisher.class);

    /**
     * 将 Trace 事件序列化为结构化日志，作为未配置 MQ 或 MQ 投递失败时的本地证据。
     *
     * @param event Trace 事件
     */
    @Override
    public void publish(TraceEvent event) {
        log.info("trace event type={} traceId={} spanId={} op={} status={} durationMs={}",
                event.getEventType(),
                event.getTraceId(),
                event.getSpanId(),
                event.getOperation(),
                event.getStatus(),
                event.getDurationMs());
    }
}
