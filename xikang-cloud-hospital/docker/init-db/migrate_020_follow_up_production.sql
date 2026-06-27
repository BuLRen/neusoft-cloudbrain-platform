-- 随访生产化：在管池、时序指标、检验映射（C 类表）
-- 可重复执行；不修改任何现有 A 类核心业务表

CREATE TABLE IF NOT EXISTS follow_up_enrollment (
    register_id                 INTEGER PRIMARY KEY REFERENCES register(id),
    managing_department_id      INTEGER NOT NULL REFERENCES department(id),
    priority_level              VARCHAR(16) NOT NULL DEFAULT 'normal',
    interview_interval_days     INTEGER NOT NULL DEFAULT 7,
    observation_interval_days   INTEGER NOT NULL DEFAULT 1,
    enrolled_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    enrolled_by                 INTEGER REFERENCES employee(id),
    status                      VARCHAR(16) NOT NULL DEFAULT 'active',
    CONSTRAINT chk_fue_priority CHECK (priority_level IN ('normal', 'high', 'critical')),
    CONSTRAINT chk_fue_status CHECK (status IN ('active', 'paused', 'closed'))
);

CREATE INDEX IF NOT EXISTS idx_fue_dept ON follow_up_enrollment(managing_department_id);

COMMENT ON TABLE follow_up_enrollment IS '随访在管患者池（生产）';

CREATE TABLE IF NOT EXISTS patient_health_observation (
    id              SERIAL PRIMARY KEY,
    register_id     INTEGER NOT NULL REFERENCES register(id),
    observed_at     TIMESTAMP NOT NULL,
    metric_code     VARCHAR(64) NOT NULL,
    metric_value    NUMERIC(12, 4) NOT NULL,
    unit            VARCHAR(16),
    source_type     VARCHAR(32) NOT NULL,
    source_ref_id   INTEGER,
    note            TEXT,
    CONSTRAINT uk_pho_register_metric_time UNIQUE (register_id, metric_code, observed_at)
);

CREATE INDEX IF NOT EXISTS idx_pho_register_time ON patient_health_observation(register_id, observed_at DESC);

COMMENT ON TABLE patient_health_observation IS '患者健康指标时序观测（生产）';

CREATE TABLE IF NOT EXISTS lab_metric_mapping (
    id              SERIAL PRIMARY KEY,
    source_type     VARCHAR(16) NOT NULL,
    source_key      VARCHAR(128) NOT NULL,
    metric_code     VARCHAR(64) NOT NULL,
    unit            VARCHAR(16),
    CONSTRAINT uk_lmm_source UNIQUE (source_type, source_key)
);

COMMENT ON TABLE lab_metric_mapping IS '检验/检查字段到标准 metric_code 映射';

-- 常用映射种子
INSERT INTO lab_metric_mapping (source_type, source_key, metric_code, unit) VALUES
    ('inspection', 'blood_glucose', 'blood_glucose', 'mmol/L'),
    ('inspection', 'glucose', 'blood_glucose', 'mmol/L'),
    ('inspection', '血糖', 'blood_glucose', 'mmol/L'),
    ('inspection', 'spo2', 'spo2', '%'),
    ('inspection', '血氧', 'spo2', '%'),
    ('inspection', 'heart_rate', 'heart_rate', '次/分'),
    ('inspection', '心率', 'heart_rate', '次/分'),
    ('check', 'blood_pressure_systolic', 'blood_pressure_systolic', 'mmHg'),
    ('check', 'blood_pressure_diastolic', 'blood_pressure_diastolic', 'mmHg'),
    ('check', 'body_temperature', 'body_temperature', '℃')
ON CONFLICT (source_type, source_key) DO NOTHING;

-- B 类 profile → C 类 enrollment
INSERT INTO follow_up_enrollment (
    register_id, managing_department_id, priority_level,
    interview_interval_days, observation_interval_days, enrolled_at, status
)
SELECT
    fp.register_id,
    fp.department_id,
    fp.priority_level,
    fp.interview_interval_days,
    fp.observation_interval_days,
    COALESCE(fp.enrolled_at, CURRENT_TIMESTAMP),
    'active'
FROM follow_up_patient_profile fp
ON CONFLICT (register_id) DO UPDATE SET
    managing_department_id = EXCLUDED.managing_department_id,
    priority_level = EXCLUDED.priority_level,
    interview_interval_days = EXCLUDED.interview_interval_days,
    observation_interval_days = EXCLUDED.observation_interval_days;

-- B 类模拟指标 → C 类（保留 source_type=simulated 标记来源）
INSERT INTO patient_health_observation (
    register_id, observed_at, metric_code, metric_value, unit, source_type, source_ref_id, note
)
SELECT
    m.register_id,
    COALESCE(m.recorded_at, m.record_date::timestamp + TIME '09:00:00'),
    m.metric_key,
    m.metric_value,
    m.unit,
    CASE WHEN m.source = 'simulated' THEN 'simulated' ELSE 'legacy_metric' END,
    m.id,
    m.note
FROM follow_up_health_metric m
ON CONFLICT (register_id, metric_code, observed_at) DO NOTHING;
