package com.css.mallorderagent.feedback;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FeedbackEventOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackEventOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(FeedbackEvent event, String payloadJson) {
        jdbcTemplate.update("""
                INSERT INTO agent_feedback_event_outbox(
                    event_id, schema_version, event_type, producer, aggregate_id,
                    aggregate_version, trace_id, occurred_at, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.schemaVersion(), event.eventType(), event.producer(),
                event.aggregateId(), event.aggregateVersion(), event.traceId(),
                Timestamp.valueOf(event.occurredAt()), payloadJson);
    }

    public List<OutboxRow> findReady(int limit) {
        return jdbcTemplate.query("""
                SELECT id, event_id, payload_json, attempts
                FROM agent_feedback_event_outbox
                WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6)
                ORDER BY id LIMIT ?
                """, (rs, rowNum) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"),
                rs.getString("payload_json"), rs.getInt("attempts")), limit);
    }

    public void markPublished(long id) {
        jdbcTemplate.update("""
                UPDATE agent_feedback_event_outbox
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(6), last_error = NULL
                WHERE id = ? AND status = 'PENDING'
                """, id);
    }

    public void markFailed(long id, int attempts, LocalDateTime availableAt, String error,
                           boolean dead) {
        jdbcTemplate.update("""
                UPDATE agent_feedback_event_outbox
                SET status = ?, attempts = ?, available_at = ?, last_error = ?
                WHERE id = ? AND status = 'PENDING'
                """, dead ? "DEAD" : "PENDING", attempts, Timestamp.valueOf(availableAt),
                truncate(error), id);
    }

    public boolean replayDead(long id) {
        return jdbcTemplate.update("""
                UPDATE agent_feedback_event_outbox
                SET status = 'PENDING', available_at = CURRENT_TIMESTAMP(6),
                    last_error = NULL
                WHERE id = ? AND status = 'DEAD'
                """, id) > 0;
    }

    private static String truncate(String error) {
        if (error == null) return null;
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public record OutboxRow(long id, String eventId, String payloadJson, int attempts) { }
}
