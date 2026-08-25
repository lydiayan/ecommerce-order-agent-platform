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
