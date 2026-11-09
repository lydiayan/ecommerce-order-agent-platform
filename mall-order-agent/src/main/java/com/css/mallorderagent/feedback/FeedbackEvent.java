package com.css.mallorderagent.feedback;

import java.time.LocalDateTime;
import java.util.Map;

/** Versioned, metadata-only event shared with AgentInsight. */
public record FeedbackEvent(
        String eventId,
        String schemaVersion,
        String eventType,
        String producer,
        String aggregateId,
        long aggregateVersion,
        String traceId,
        LocalDateTime occurredAt,
        Map<String, Object> payload) {
}
