package com.css.mallorderagent.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class FeedbackEventOutboxService {

    private final FeedbackEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public FeedbackEventOutboxService(FeedbackEventOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 构造带版本和 Trace 的反馈领域事件，序列化后写入本地 Outbox。
     *
     * @param eventType 事件类型
     * @param aggregateId 回复或坏案例等聚合根编号
     * @param aggregateVersion 聚合根版本，用于消费者幂等更新
     * @param traceId 关联的 Agent Trace ID
     * @param payload 事件业务字段
     */
    public void append(String eventType, String aggregateId, long aggregateVersion,
                       String traceId, Map<String, Object> payload) {
        FeedbackEvent event = new FeedbackEvent(UUID.randomUUID().toString(), "1.0", eventType,
                "mall-order-agent", aggregateId, aggregateVersion, traceId, LocalDateTime.now(), payload);
        try {
            repository.append(event, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize feedback event", e);
        }
    }
}
