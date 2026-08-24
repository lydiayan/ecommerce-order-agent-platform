package com.css.mallorderagent.feedback;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertResponse(ResponseSnapshotInsert row) {
        jdbcTemplate.update("""
                INSERT INTO agent_response_snapshot(
                    response_id, app_user_id, actor_user_fingerprint, trace_id, plan_strategy,
                    model_name, agent_version, grounded, interrupted, query_ciphertext,
                    answer_ciphertext, conversation_ciphertext, tool_summary_ciphertext,
                    operation_ciphertext, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.responseId(), row.appUserId(), row.actorUserFingerprint(), row.traceId(),
                row.planStrategy(), row.modelName(), row.agentVersion(), row.grounded(), row.interrupted(),
                row.queryCiphertext(), row.answerCiphertext(), row.conversationCiphertext(),
                row.toolSummaryCiphertext(), row.operationCiphertext(), Timestamp.valueOf(row.expiresAt()));
    }

    public boolean responseOwnedBy(String responseId, long appUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM agent_response_snapshot
                WHERE response_id = ? AND app_user_id = ? AND expires_at > CURRENT_TIMESTAMP
                """, Integer.class, responseId, appUserId);
        return count != null && count > 0;
    }

    public Optional<FeedbackRow> findFeedback(String responseId, long appUserId) {
        return jdbcTemplate.query("""
                SELECT id, rating, reasons_json, comment_ciphertext, updated_at
                FROM agent_response_feedback WHERE response_id = ? AND app_user_id = ?
                """, (rs, rowNum) -> new FeedbackRow(
                rs.getLong("id"), rs.getString("rating"), rs.getString("reasons_json"),
                rs.getString("comment_ciphertext"), toLocalDateTime(rs.getTimestamp("updated_at"))),
                responseId, appUserId).stream().findFirst();
    }

    public FeedbackRow upsertFeedback(String responseId, long appUserId, String rating,
                                      String reasonsJson, String commentCiphertext) {
        jdbcTemplate.update("""
                INSERT INTO agent_response_feedback(
                    response_id, app_user_id, rating, reasons_json, comment_ciphertext)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    rating = VALUES(rating), reasons_json = VALUES(reasons_json),
                    comment_ciphertext = VALUES(comment_ciphertext), updated_at = CURRENT_TIMESTAMP
                """, responseId, appUserId, rating, reasonsJson, commentCiphertext);
        return findFeedback(responseId, appUserId)
                .orElseThrow(() -> new IllegalStateException("Feedback upsert returned no row"));
    }

    public boolean deleteFeedback(String responseId, long appUserId) {
        return jdbcTemplate.update("""
                DELETE FROM agent_response_feedback WHERE response_id = ? AND app_user_id = ?
                """, responseId, appUserId) > 0;
    }

    public void insertFeedbackHistory(String responseId, long appUserId, String action,
                                      String previousRating, String currentRating, String reasonsJson) {
        jdbcTemplate.update("""
                INSERT INTO agent_feedback_history(
                    response_id, app_user_id, action, previous_rating, current_rating, reasons_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """, responseId, appUserId, action, previousRating, currentRating, reasonsJson);
    }

    public Optional<BadCaseIdentity> findBadCaseIdentity(String responseId) {
        return jdbcTemplate.query("""
                SELECT id, status FROM agent_bad_case WHERE response_id = ?
                """, (rs, rowNum) -> new BadCaseIdentity(
                rs.getLong("id"), rs.getString("status")), responseId).stream().findFirst();
    }

    public BadCaseIdentity openBadCase(String responseId, String priority) {
        jdbcTemplate.update("""
                INSERT INTO agent_bad_case(response_id, status, priority, last_feedback_at)
                VALUES (?, 'NEW', ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    status = IF(status = 'IGNORED', 'NEW', status),
                    priority = IF(? = 'URGENT', 'URGENT', priority),
                    last_feedback_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """, responseId, priority, priority);
        return findBadCaseIdentity(responseId)
                .orElseThrow(() -> new IllegalStateException("Bad case upsert returned no row"));
    }

    public Optional<BadCaseIdentity> ignoreNewBadCase(String responseId) {
        Optional<BadCaseIdentity> current = findBadCaseIdentity(responseId);
        if (current.isEmpty() || !BadCaseStatus.NEW.name().equals(current.get().status())) {
            return Optional.empty();
        }
        int changed = jdbcTemplate.update("""
                UPDATE agent_bad_case SET status = 'IGNORED', updated_at = CURRENT_TIMESTAMP
                WHERE response_id = ? AND status = 'NEW'
                """, responseId);
        return changed > 0 ? current : Optional.empty();
    }

    public void insertBadCaseHistory(long badCaseId, Long changedByUserId,
                                     String fromStatus, String toStatus, String detailsCiphertext) {
        jdbcTemplate.update("""
                INSERT INTO agent_bad_case_history(
                    bad_case_id, changed_by_user_id, from_status, to_status, details_ciphertext)
                VALUES (?, ?, ?, ?, ?)
                """, badCaseId, changedByUserId, fromStatus, toStatus, detailsCiphertext);
    }

    public List<BadCaseListRow> findBadCases(String status, String reason, String strategy,
                                             String modelName, String agentVersion,
                                             LocalDateTime from, LocalDateTime toExclusive, int limit) {
        String reasonPattern = reason != null ? "%\"" + reason + "\"%" : null;
        Timestamp fromTimestamp = from != null ? Timestamp.valueOf(from) : null;
        Timestamp toTimestamp = toExclusive != null ? Timestamp.valueOf(toExclusive) : null;
        return jdbcTemplate.query("""
                SELECT bc.id, bc.response_id, bc.status, bc.priority, bc.category,
                       bc.owner_username, bc.fix_version, bc.created_at, bc.updated_at,
                       r.trace_id, r.plan_strategy, r.model_name, r.agent_version,
                       f.reasons_json
                FROM agent_bad_case bc
                JOIN agent_response_snapshot r ON r.response_id = bc.response_id
                LEFT JOIN agent_response_feedback f ON f.response_id = bc.response_id
                WHERE (? IS NULL OR bc.status = ?)
                  AND (? IS NULL OR f.reasons_json LIKE ?)
                  AND (? IS NULL OR r.plan_strategy = ?)
                  AND (? IS NULL OR r.model_name = ?)
                  AND (? IS NULL OR r.agent_version = ?)
                  AND (? IS NULL OR r.created_at >= ?)
                  AND (? IS NULL OR r.created_at < ?)
                ORDER BY CASE WHEN bc.priority = 'URGENT' THEN 0 ELSE 1 END, bc.updated_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new BadCaseListRow(
                rs.getLong("id"), rs.getString("response_id"), rs.getString("status"),
                rs.getString("priority"), rs.getString("category"), rs.getString("owner_username"),
                rs.getString("fix_version"), rs.getString("trace_id"), rs.getString("plan_strategy"),
                rs.getString("model_name"), rs.getString("agent_version"), rs.getString("reasons_json"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))),
                status, status, reason, reasonPattern, strategy, strategy,
                modelName, modelName, agentVersion, agentVersion,
                fromTimestamp, fromTimestamp, toTimestamp, toTimestamp, limit);
    }

    public Optional<BadCaseDetailRow> findBadCase(long badCaseId) {
        return jdbcTemplate.query("""
                SELECT bc.id, bc.response_id, bc.status, bc.priority, bc.category,
                       bc.owner_username, bc.root_cause_ciphertext, bc.resolution_ciphertext,
                       bc.fix_version, bc.created_at, bc.updated_at,
                       r.trace_id, r.plan_strategy, r.model_name, r.agent_version,
                       r.grounded, r.interrupted, r.query_ciphertext, r.answer_ciphertext,
                       r.conversation_ciphertext, r.tool_summary_ciphertext, r.operation_ciphertext,
                       f.reasons_json, f.comment_ciphertext
                FROM agent_bad_case bc
                JOIN agent_response_snapshot r ON r.response_id = bc.response_id
                LEFT JOIN agent_response_feedback f ON f.response_id = bc.response_id
                WHERE bc.id = ?
                """, (rs, rowNum) -> new BadCaseDetailRow(
                rs.getLong("id"), rs.getString("response_id"), rs.getString("status"),
                rs.getString("priority"), rs.getString("category"), rs.getString("owner_username"),
                rs.getString("root_cause_ciphertext"), rs.getString("resolution_ciphertext"),
                rs.getString("fix_version"), rs.getString("trace_id"), rs.getString("plan_strategy"),
                rs.getString("model_name"), rs.getString("agent_version"), rs.getBoolean("grounded"),
                rs.getBoolean("interrupted"), rs.getString("query_ciphertext"),
                rs.getString("answer_ciphertext"), rs.getString("conversation_ciphertext"),
                rs.getString("tool_summary_ciphertext"), rs.getString("operation_ciphertext"),
                rs.getString("reasons_json"),
                rs.getString("comment_ciphertext"), toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))), badCaseId).stream().findFirst();
    }

    public void updateBadCase(long badCaseId, String status, String category, String ownerUsername,
                              String rootCauseCiphertext, String resolutionCiphertext, String fixVersion) {
        jdbcTemplate.update("""
                UPDATE agent_bad_case
                SET status = ?, category = ?, owner_username = ?, root_cause_ciphertext = ?,
                    resolution_ciphertext = ?, fix_version = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, category, ownerUsername, rootCauseCiphertext,
                resolutionCiphertext, fixVersion, badCaseId);
    }

    public MetricsRow metrics(int days) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.response_id) AS response_count,
                       COUNT(f.id) AS feedback_count,
                       COALESCE(SUM(CASE WHEN f.rating = 'UP' THEN 1 ELSE 0 END), 0) AS up_count,
                       COALESCE(SUM(CASE WHEN f.rating = 'DOWN' THEN 1 ELSE 0 END), 0) AS down_count,
                       COALESCE(SUM(CASE WHEN bc.status NOT IN ('IGNORED', 'NEW') THEN 1 ELSE 0 END), 0)
                           AS triaged_count,
                       COALESCE(SUM(CASE WHEN bc.status = 'RESOLVED' THEN 1 ELSE 0 END), 0)
                           AS resolved_count
                FROM agent_response_snapshot r
                LEFT JOIN agent_response_feedback f ON f.response_id = r.response_id
                LEFT JOIN agent_bad_case bc ON bc.response_id = r.response_id
                WHERE r.created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? DAY)
                """, (rs, rowNum) -> new MetricsRow(
                rs.getLong("response_count"), rs.getLong("feedback_count"), rs.getLong("up_count"),
                rs.getLong("down_count"), rs.getLong("triaged_count"), rs.getLong("resolved_count")), days);
    }

    public void rollupDailyMetrics() {
        jdbcTemplate.update("""
                INSERT INTO agent_feedback_daily_metric(
                    metric_date, plan_strategy, model_name, response_count,
                    feedback_count, up_count, down_count)
                SELECT DATE(r.created_at), r.plan_strategy, r.model_name, COUNT(DISTINCT r.response_id),
                       COUNT(f.id),
                       COALESCE(SUM(CASE WHEN f.rating = 'UP' THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN f.rating = 'DOWN' THEN 1 ELSE 0 END), 0)
                FROM agent_response_snapshot r
                LEFT JOIN agent_response_feedback f ON f.response_id = r.response_id
                WHERE r.created_at < CURRENT_DATE
                GROUP BY DATE(r.created_at), r.plan_strategy, r.model_name
                ON DUPLICATE KEY UPDATE
                    response_count = VALUES(response_count), feedback_count = VALUES(feedback_count),
                    up_count = VALUES(up_count), down_count = VALUES(down_count)
                """);
    }

    public int purgeExpiredResponses() {
        return jdbcTemplate.update("""
                DELETE FROM agent_response_snapshot
                WHERE expires_at < CURRENT_TIMESTAMP
                LIMIT 1000
                """);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    public record ResponseSnapshotInsert(
            String responseId, long appUserId, String actorUserFingerprint, String traceId,
            String planStrategy, String modelName, String agentVersion, boolean grounded,
            boolean interrupted, String queryCiphertext, String answerCiphertext,
            String conversationCiphertext, String toolSummaryCiphertext,
            String operationCiphertext, LocalDateTime expiresAt) {
    }

    public record FeedbackRow(long id, String rating, String reasonsJson,
                              String commentCiphertext, LocalDateTime updatedAt) {
    }

    public record BadCaseIdentity(long id, String status) {
    }

    public record BadCaseListRow(
            long id, String responseId, String status, String priority, String category,
            String ownerUsername, String fixVersion, String traceId, String planStrategy,
            String modelName, String agentVersion, String reasonsJson,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record BadCaseDetailRow(
            long id, String responseId, String status, String priority, String category,
            String ownerUsername, String rootCauseCiphertext, String resolutionCiphertext,
            String fixVersion, String traceId, String planStrategy, String modelName,
            String agentVersion, boolean grounded, boolean interrupted, String queryCiphertext,
            String answerCiphertext, String conversationCiphertext, String toolSummaryCiphertext,
            String operationCiphertext, String reasonsJson, String commentCiphertext, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record MetricsRow(long responseCount, long feedbackCount, long upCount, long downCount,
                             long triagedCount, long resolvedCount) {
    }
}
