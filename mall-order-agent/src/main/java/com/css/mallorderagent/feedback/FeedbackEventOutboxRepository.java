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

    /**
     * 在本地事务中追加一条待发布反馈事件。
     *
     * @param event 反馈领域事件元数据
     * @param payloadJson 完整事件 JSON
     */
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

    /**
     * 查询已到重试时间的待发布事件。
     *
     * @param limit 最大返回条数
     * @return 按主键顺序排列的待发布事件
     */
    public List<OutboxRow> findReady(int limit) {
        return jdbcTemplate.query("""
                SELECT id, event_id, payload_json, attempts
                FROM agent_feedback_event_outbox
                WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6)
                ORDER BY id LIMIT ?
                """, (rs, rowNum) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"),
                rs.getString("payload_json"), rs.getInt("attempts")), limit);
    }

    /**
     * 将仍处于 PENDING 的事件标记为已发布。
     *
     * @param id Outbox 记录主键
     */
    public void markPublished(long id) {
        jdbcTemplate.update("""
                UPDATE agent_feedback_event_outbox
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(6), last_error = NULL
                WHERE id = ? AND status = 'PENDING'
                """, id);
    }

    /**
     * 记录发布失败次数，并安排下次重试或转入死信状态。
     *
     * @param id Outbox 记录主键
     * @param attempts 累计发布尝试次数
     * @param availableAt 下次允许重试的时间
     * @param error 最近一次错误信息
     * @param dead 是否已达到最大尝试次数
     */
    public void markFailed(long id, int attempts, LocalDateTime availableAt, String error,
                           boolean dead) {
        jdbcTemplate.update("""
                UPDATE agent_feedback_event_outbox
                SET status = ?, attempts = ?, available_at = ?, last_error = ?
                WHERE id = ? AND status = 'PENDING'
                """, dead ? "DEAD" : "PENDING", attempts, Timestamp.valueOf(availableAt),
                truncate(error), id);
    }

    /**
     * 将指定死信事件恢复为立即可发布的 PENDING 状态。
     *
     * @param id Outbox 记录主键
     * @return 记录存在且原状态为 DEAD 时返回 true
     */
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
