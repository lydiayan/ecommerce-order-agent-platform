CREATE TABLE IF NOT EXISTS agent_response_snapshot (
    response_id CHAR(36) NOT NULL,
    app_user_id BIGINT NOT NULL,
    actor_user_fingerprint CHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    plan_strategy VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    grounded BOOLEAN NOT NULL DEFAULT FALSE,
    interrupted BOOLEAN NOT NULL DEFAULT FALSE,
    query_ciphertext MEDIUMTEXT NOT NULL,
    answer_ciphertext MEDIUMTEXT NOT NULL,
    conversation_ciphertext TEXT NULL,
    tool_summary_ciphertext MEDIUMTEXT NULL,
    operation_ciphertext TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (response_id),
    INDEX idx_agent_response_user_created (app_user_id, created_at),
    INDEX idx_agent_response_trace (trace_id),
    INDEX idx_agent_response_expiry (expires_at),
    CONSTRAINT fk_agent_response_user FOREIGN KEY (app_user_id)
        REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_response_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id CHAR(36) NOT NULL,
    app_user_id BIGINT NOT NULL,
    rating VARCHAR(8) NOT NULL,
    reasons_json VARCHAR(1000) NOT NULL DEFAULT '[]',
    comment_ciphertext TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_feedback_response_user (response_id, app_user_id),
    INDEX idx_agent_feedback_rating_updated (rating, updated_at),
    CONSTRAINT fk_agent_feedback_response FOREIGN KEY (response_id)
        REFERENCES agent_response_snapshot(response_id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_feedback_user FOREIGN KEY (app_user_id)
        REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_feedback_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id CHAR(36) NOT NULL,
    app_user_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    previous_rating VARCHAR(8) NULL,
    current_rating VARCHAR(8) NULL,
    reasons_json VARCHAR(1000) NOT NULL DEFAULT '[]',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_feedback_history_response (response_id, created_at),
    CONSTRAINT fk_agent_feedback_history_response FOREIGN KEY (response_id)
        REFERENCES agent_response_snapshot(response_id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_feedback_history_user FOREIGN KEY (app_user_id)
        REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_bad_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'NEW',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    category VARCHAR(64) NULL,
    owner_username VARCHAR(64) NULL,
    root_cause_ciphertext TEXT NULL,
    resolution_ciphertext TEXT NULL,
    fix_version VARCHAR(64) NULL,
    last_feedback_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_bad_case_response (response_id),
    INDEX idx_agent_bad_case_status_updated (status, updated_at),
    INDEX idx_agent_bad_case_priority_updated (priority, updated_at),
    CONSTRAINT fk_agent_bad_case_response FOREIGN KEY (response_id)
        REFERENCES agent_response_snapshot(response_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_bad_case_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bad_case_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    details_ciphertext TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_bad_case_history_case (bad_case_id, created_at),
    CONSTRAINT fk_agent_bad_case_history_case FOREIGN KEY (bad_case_id)
        REFERENCES agent_bad_case(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_bad_case_history_user FOREIGN KEY (changed_by_user_id)
        REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_feedback_daily_metric (
    metric_date DATE NOT NULL,
    plan_strategy VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    response_count BIGINT NOT NULL DEFAULT 0,
    feedback_count BIGINT NOT NULL DEFAULT 0,
    up_count BIGINT NOT NULL DEFAULT 0,
    down_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (metric_date, plan_strategy, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
