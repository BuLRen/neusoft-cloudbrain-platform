-- migrate_036_critical_value.sql
-- 危急值闭环：工单表 + 阈值规则表（与 docker/init-db 同步）

CREATE TABLE IF NOT EXISTS critical_value_alert (
    id                  BIGSERIAL PRIMARY KEY,
    register_id         BIGINT       NOT NULL,
    patient_name        VARCHAR(64),
    case_number         VARCHAR(64),
    source_type         VARCHAR(16)  NOT NULL,
    source_id           BIGINT       NOT NULL,
    tech_name           VARCHAR(128),
    critical_items      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    severity            VARCHAR(16)  NOT NULL DEFAULT 'CRITICAL',
    reporter_id         BIGINT,
    reporter_name       VARCHAR(64),
    doctor_id           BIGINT       NOT NULL,
    doctor_name         VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reported_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_time   TIMESTAMP,
    handled_time        TIMESTAMP,
    handle_note         VARCHAR(500),
    escalated_time      TIMESTAMP,
    ack_deadline        TIMESTAMP    NOT NULL,
    creation_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cva_doctor_status ON critical_value_alert (doctor_id, status);
CREATE INDEX IF NOT EXISTS idx_cva_status_deadline ON critical_value_alert (status, ack_deadline);
CREATE INDEX IF NOT EXISTS idx_cva_register ON critical_value_alert (register_id);

CREATE TABLE IF NOT EXISTS critical_value_rule (
    id              BIGSERIAL PRIMARY KEY,
    tech_code       VARCHAR(64)  NOT NULL DEFAULT '*',
    field_key       VARCHAR(64),
    item_name       VARCHAR(128) NOT NULL,
    unit            VARCHAR(32),
    critical_low    NUMERIC(12, 4),
    critical_high   NUMERIC(12, 4),
    severity        VARCHAR(16)  NOT NULL DEFAULT 'CRITICAL',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    creation_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cvr_item_name ON critical_value_rule (item_name) WHERE enabled = TRUE;

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'potassium', '血钾', 'mmol/L', 2.5, 6.5, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血钾');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'glucose', '血糖', 'mmol/L', 2.2, 22.2, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血糖');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'calcium', '血钙', 'mmol/L', 1.6, 3.5, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血钙');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'sodium', '血钠', 'mmol/L', 120, 160, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血钠');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'wbc', '白细胞', '10^9/L', 1.0, 30.0, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '白细胞');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'platelet', '血小板', '10^9/L', 20, NULL, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血小板');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'hemoglobin', '血红蛋白', 'g/L', 50, NULL, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '血红蛋白');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'troponin', '肌钙蛋白', 'ng/mL', NULL, 0.5, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '肌钙蛋白');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'creatinine', '肌酐', 'umol/L', NULL, 707, 'URGENT'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = '肌酐');

INSERT INTO critical_value_rule (tech_code, field_key, item_name, unit, critical_low, critical_high, severity)
SELECT '*', 'ph', 'pH', '', 7.2, 7.6, 'CRITICAL'
WHERE NOT EXISTS (SELECT 1 FROM critical_value_rule WHERE item_name = 'pH');
