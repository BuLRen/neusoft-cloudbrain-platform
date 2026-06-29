-- ============================================================
-- 迁移脚本：migrate_022_ai_drug_suggestion
-- 说明：W5 智能荐药 AI 建议落库表
-- 幂等：IF NOT EXISTS
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_drug_suggestion (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL,
    drug_id             INTEGER         DEFAULT NULL,
    drug_name           VARCHAR(255)    DEFAULT NULL,
    recommend_usage     TEXT            DEFAULT NULL,
    recommend_quantity  INTEGER         DEFAULT NULL,
    confidence          DECIMAL(5,2)    DEFAULT NULL,
    recommendation_basis TEXT           DEFAULT NULL,
    caution_notes       TEXT            DEFAULT NULL,
    is_adopted          SMALLINT        NOT NULL DEFAULT 0,
    sort_order          INTEGER         NOT NULL DEFAULT 1,
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id            VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_drug_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_drug_drug
        FOREIGN KEY (drug_id) REFERENCES drug_info(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_drug_adopted CHECK (is_adopted IN (0, 1)),
    CONSTRAINT chk_ai_drug_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 100)),
    CONSTRAINT chk_ai_drug_quantity CHECK (recommend_quantity IS NULL OR recommend_quantity >= 1)
);

CREATE INDEX IF NOT EXISTS idx_ai_drug_register_id ON ai_drug_suggestion(register_id);
CREATE INDEX IF NOT EXISTS idx_ai_drug_drug_id ON ai_drug_suggestion(drug_id);

COMMENT ON TABLE ai_drug_suggestion IS 'AI 智能荐药建议表（W5 工作流产出）';
COMMENT ON COLUMN ai_drug_suggestion.confidence IS '推荐置信度 0-100';
