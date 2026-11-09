CREATE TABLE IF NOT EXISTS skill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active_version_id BIGINT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_name (name),
    KEY idx_skill_active_version (active_version_id),
    KEY idx_skill_enabled (enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS skill_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    skill_id BIGINT NOT NULL,
    package_digest CHAR(64) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_ref VARCHAR(255) NOT NULL,
    source_path VARCHAR(500) NOT NULL DEFAULT '',
    storage_key VARCHAR(255) NOT NULL,
    instructions MEDIUMTEXT NOT NULL,
    required_actions VARCHAR(1000) NOT NULL DEFAULT '[]',
    trigger_keywords VARCHAR(2000) NOT NULL DEFAULT '[]',
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_version_digest (skill_id, package_digest),
    KEY idx_skill_version_created (skill_id, created_at),
    CONSTRAINT fk_skill_version_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS skill_policy (
    skill_id BIGINT NOT NULL,
    allow_implicit BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (skill_id),
    CONSTRAINT fk_skill_policy_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE skill
    ADD CONSTRAINT fk_skill_active_version FOREIGN KEY (active_version_id)
        REFERENCES skill_version(id) ON DELETE SET NULL;
