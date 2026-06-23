-- migrate_009_clinical_record.sql
-- 患者公共病历本：挂号归档字段 + 患者长期临床档案

ALTER TABLE register
    ADD COLUMN IF NOT EXISTS clinical_archived_at TIMESTAMP DEFAULT NULL;

ALTER TABLE register
    ADD COLUMN IF NOT EXISTS clinical_archived_by INTEGER DEFAULT NULL;

COMMENT ON COLUMN register.clinical_archived_at IS '病历归档发布时间，NULL 表示患者端不可见完整病历';
COMMENT ON COLUMN register.clinical_archived_by IS '归档操作医生 employee_id';

CREATE TABLE IF NOT EXISTS patient_clinical_profile (
    patient_id              INTEGER PRIMARY KEY REFERENCES patient(id),
    allergy_summary         TEXT,
    chronic_conditions      TEXT,
    past_diagnosis_summary  TEXT,
    last_visit_at           TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE patient_clinical_profile IS '患者长期临床档案（跨就诊汇总）';

CREATE INDEX IF NOT EXISTS idx_register_clinical_archived
    ON register (patient_id, clinical_archived_at DESC NULLS LAST);
