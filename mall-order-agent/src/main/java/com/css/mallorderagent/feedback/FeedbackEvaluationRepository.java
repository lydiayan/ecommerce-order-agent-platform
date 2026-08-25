package com.css.mallorderagent.feedback;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackEvaluationRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackEvaluationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(FeedbackEvaluationEvent event) {
        jdbcTemplate.update("""
                INSERT INTO agent_response_evaluation(
                    response_id, trace_id, evaluation_status, evaluation_version, score_total, score_max,
                    passed, evaluation_detail, evaluator_version, evaluated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    trace_id = IF(VALUES(evaluation_version) >= evaluation_version, VALUES(trace_id), trace_id),
                    evaluation_status = IF(VALUES(evaluation_version) >= evaluation_version,
                        VALUES(evaluation_status), evaluation_status),
                    score_total = IF(VALUES(evaluation_version) >= evaluation_version, VALUES(score_total), score_total),
                    score_max = IF(VALUES(evaluation_version) >= evaluation_version, VALUES(score_max), score_max),
                    passed = IF(VALUES(evaluation_version) >= evaluation_version, VALUES(passed), passed),
                    evaluation_detail = IF(VALUES(evaluation_version) >= evaluation_version,
                        VALUES(evaluation_detail), evaluation_detail),
                    evaluator_version = IF(VALUES(evaluation_version) >= evaluation_version,
                        VALUES(evaluator_version), evaluator_version),
                    evaluated_at = IF(VALUES(evaluation_version) >= evaluation_version,
                        VALUES(evaluated_at), evaluated_at),
                    evaluation_version = GREATEST(evaluation_version, VALUES(evaluation_version)),
                    updated_at = CURRENT_TIMESTAMP(6)
                """, event.responseId(), event.traceId(), event.evaluationStatus(), event.evaluationVersion(),
                event.scoreTotal(), event.scoreMax(), event.passed(), event.evaluationDetail(),
                event.evaluatorVersion(), event.evaluatedAt());
    }

    public record FeedbackEvaluationEvent(String responseId, String traceId, String evaluationStatus,
                                          long evaluationVersion, Integer scoreTotal, Integer scoreMax, Boolean passed,
                                          String evaluationDetail, String evaluatorVersion,
                                          java.sql.Timestamp evaluatedAt) { }
}
