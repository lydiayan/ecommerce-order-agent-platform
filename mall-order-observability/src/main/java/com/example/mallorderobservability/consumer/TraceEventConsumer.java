package com.example.mallorderobservability.consumer;

import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.example.mallorderobservability.storage.ElasticsearchTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "observability.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${observability.trace.topic:rag-trace-events}",
        consumerGroup = "${observability.consumer.group:rag-trace-consumer}",
        selectorExpression = "${observability.trace.tag:trace}"
)
public class TraceEventConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(TraceEventConsumer.class);

    private final ElasticsearchTraceRepository repository;
    private final ObjectMapper objectMapper;

    public TraceEventConsumer(ElasticsearchTraceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        try {
            TraceEvent event = objectMapper.readValue(message, TraceEvent.class);
            repository.save(event);
            log.debug("indexed trace event traceId={} type={}", event.getTraceId(), event.getEventType());
        } catch (Exception e) {
            log.error("failed to consume trace event: {}", e.getMessage(), e);
        }
    }
}
