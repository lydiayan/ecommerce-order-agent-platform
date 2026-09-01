ALTER TABLE agent_response_snapshot
    ADD COLUMN intent VARCHAR(64) NULL COMMENT '归一化意图' AFTER plan_strategy,
    ADD COLUMN intent_source VARCHAR(16) NULL COMMENT 'RULE、LLM 或 FALLBACK' AFTER intent,
    ADD COLUMN intent_confidence DECIMAL(6,5) NULL COMMENT '意图分类置信度' AFTER intent_source,
    ADD COLUMN rule_match_status VARCHAR(16) NULL COMMENT 'MATCH、AMBIGUOUS 或 NO_MATCH' AFTER intent_confidence,
    ADD COLUMN clarification_required BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '是否因意图不确定要求用户澄清' AFTER rule_match_status;

CREATE INDEX idx_agent_response_intent_source
    ON agent_response_snapshot(intent, intent_source, created_at);
