package com.example.mallorderobservability.trace;

import com.example.mallorderobservability.config.ObservabilityProperties;
import com.example.mallorderobservability.model.TraceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 通过 RocketMQ 异步发送 Trace 事件。
 */
public class RocketMqTracePublisher implements TracePublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqTracePublisher.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;
    private final LoggingTracePublisher fallback = new LoggingTracePublisher();

    public RocketMqTracePublisher(RocketMQTemplate rocketMQTemplate,
                                    ObservabilityProperties properties,
                                    ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(TraceEvent event) {
        fallback.publish(event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            String destination = properties.getTrace().getTopic() + ":" + properties.getTrace().getTag();
            rocketMQTemplate.asyncSend(destination, MessageBuilder.withPayload(payload).build(),
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.debug("trace event sent traceId={} msgId={}",
                                    event.getTraceId(), sendResult.getMsgId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.warn("failed to send trace event traceId={}: {}",
                                    event.getTraceId(), e.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize trace event traceId={}: {}",
                    event.getTraceId(), e.getMessage());
        }
    }
}
