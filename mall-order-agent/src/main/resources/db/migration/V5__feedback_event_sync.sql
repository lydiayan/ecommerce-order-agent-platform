ALTER TABLE agent_response_feedback
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '反馈聚合版本' AFTER id;

ALTER TABLE agent_bad_case
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT 'bad case 聚合版本' AFTER id;

CREATE TABLE IF NOT EXISTS agent_feedback_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    schema_version VARCHAR(16) NOT NULL DEFAULT '1.0',
    event_type VARCHAR(64) NOT NULL,
    producer VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    trace_id VARCHAR(128) NULL,
    occurred_at DATETIME(6) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_outbox_event (event_id),
    INDEX idx_feedback_outbox_ready (status, available_at, id),
    INDEX idx_feedback_outbox_aggregate (aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈事件 Outbox';

CREATE TABLE IF NOT EXISTS agent_response_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id CHAR(36) NOT NULL,
    trace_id VARCHAR(128) NULL,
    evaluation_status VARCHAR(32) NOT NULL,
    evaluation_version BIGINT NOT NULL DEFAULT 0,
    score_total INT NULL,
    score_max INT NULL,
    passed BOOLEAN NULL,
    evaluation_detail JSON NULL,
    evaluator_version VARCHAR(64) NULL,
    evaluated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_evaluation_response (response_id),
    INDEX idx_response_evaluation_status (evaluation_status),
    CONSTRAINT fk_response_evaluation_response FOREIGN KEY (response_id)
        REFERENCES agent_response_snapshot(response_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentInsight 回传的线上评测摘要';
