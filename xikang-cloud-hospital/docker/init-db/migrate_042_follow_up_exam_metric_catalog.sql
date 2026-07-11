-- 随访疗效页：医技检验/检查指标展示目录（新表，不修改既有临床表与 lab_metric_mapping）
CREATE TABLE IF NOT EXISTS follow_up_exam_metric_catalog (
    id              SERIAL PRIMARY KEY,
    source_type     VARCHAR(16) NOT NULL,
    source_key      VARCHAR(128) NOT NULL,
    metric_code     VARCHAR(64) NOT NULL,
    display_label   VARCHAR(128),
    unit            VARCHAR(32),
    CONSTRAINT uk_follow_up_exam_metric_catalog UNIQUE (source_type, source_key)
);

COMMENT ON TABLE follow_up_exam_metric_catalog IS '医技结果 itemCode/字段 → 疗效页展示用 metric_code（只读映射，不写临床表）';

INSERT INTO follow_up_exam_metric_catalog (source_type, source_key, metric_code, display_label, unit) VALUES
    ('inspection', 'WBC', 'wbc', '白细胞计数', '10^9/L'),
    ('inspection', 'wbc', 'wbc', '白细胞计数', '10^9/L'),
    ('inspection', 'HGB', 'hgb', '血红蛋白', 'g/L'),
    ('inspection', 'hgb', 'hgb', '血红蛋白', 'g/L'),
    ('inspection', 'PLT', 'plt', '血小板', '10^9/L'),
    ('inspection', 'plt', 'plt', '血小板', '10^9/L'),
    ('inspection', 'NEUT%', 'neut_pct', '中性粒细胞比例', '%'),
    ('inspection', 'CRP', 'crp', 'C反应蛋白', 'mg/L'),
    ('inspection', 'crp', 'crp', 'C反应蛋白', 'mg/L'),
    ('inspection', 'hba1c', 'hba1c', '糖化血红蛋白', '%'),
    ('inspection', 'HbA1c', 'hba1c', '糖化血红蛋白', '%'),
    ('inspection', 'fasting_glucose', 'blood_glucose', '空腹血糖', 'mmol/L'),
    ('inspection', 'blood_glucose', 'blood_glucose', '血糖', 'mmol/L'),
    ('check', 'body_temperature', 'body_temperature', '体温', '℃'),
    ('check', 'blood_pressure_systolic', 'blood_pressure_systolic', '收缩压', 'mmHg'),
    ('check', 'blood_pressure_diastolic', 'blood_pressure_diastolic', '舒张压', 'mmHg')
ON CONFLICT (source_type, source_key) DO NOTHING;
