package com.example.mallorderobservability.trace;

import com.example.mallorderobservability.model.TraceEvent;

/**
 * Trace 事件发布接口。
 */
public interface TracePublisher {

    void publish(TraceEvent event);
}
