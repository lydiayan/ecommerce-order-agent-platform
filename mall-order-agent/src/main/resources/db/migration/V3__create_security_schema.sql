CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    actor_user_id VARCHAR(64) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    auth_version BIGINT NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    password_changed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    UNIQUE KEY uk_app_user_actor (actor_user_id),
    CONSTRAINT fk_app_user_actor FOREIGN KEY (actor_user_id)
        REFERENCES actor_identity(actor_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role (
    role_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_code) REFERENCES app_role(role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS role_capability (
    role_code VARCHAR(32) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, capability),
    CONSTRAINT fk_role_capability_role FOREIGN KEY (role_code) REFERENCES app_role(role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_name VARCHAR(100) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at DATETIME NULL,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_token_hash (token_hash),
    INDEX idx_api_token_prefix (token_prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS security_audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    subject_fingerprint VARCHAR(64) NULL,
    resource_fingerprint VARCHAR(64) NULL,
    outcome VARCHAR(32) NOT NULL,
    source_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    trace_id VARCHAR(64) NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_security_audit_created (created_at),
    INDEX idx_security_audit_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO app_role (role_code, display_name) VALUES
    ('HR', '人力资源'), ('ENGINEERING', '研发'), ('SALES', '销售'),
    ('CUSTOMER', '客户'), ('ADMIN', '系统管理员'), ('EVALUATOR', '评测服务');

INSERT IGNORE INTO role_capability (role_code, capability) VALUES
    ('ADMIN', 'ACCOUNT_ADMIN'), ('ADMIN', 'KNOWLEDGE_ADMIN'), ('ADMIN', 'DEMO_RESET'),
    ('ADMIN', 'TOKEN_ADMIN'), ('ADMIN', 'AUDIT_READ'),
    ('EVALUATOR', 'EVALUATION_ACT_AS');
