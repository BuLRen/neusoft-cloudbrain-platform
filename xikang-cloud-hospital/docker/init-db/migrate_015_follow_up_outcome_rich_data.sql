-- 疗效评估：丰富模拟数据 + recorded_at 时间戳
-- 可重复执行（先清理演示患者 1001-1005 的旧指标再写入）

ALTER TABLE follow_up_health_metric
    ADD COLUMN IF NOT EXISTS recorded_at TIMESTAMP DEFAULT NULL;

DELETE FROM follow_up_health_metric WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM ai_follow_up_record WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM ai_follow_up_plan WHERE id BETWEEN 9001 AND 9010;

INSERT INTO register (
    id, case_number, real_name, gender, birthdate, age, age_type, home_address,
    visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
    is_book, regist_method, regist_money, visit_state
) VALUES
    (1004, 'BL20260523004', E'赵六', E'女', '1982-11-20', 43, E'年', E'沈阳市铁西区',
     CURRENT_TIMESTAMP, E'上午', 1, 1, 2, 1, E'否', E'医保', 15.00, 3),
    (1005, 'BL20260523005', E'孙七', E'男', '1968-07-08', 57, E'年', E'沈阳市大东区',
     CURRENT_TIMESTAMP, E'下午', 2, 2, 2, 1, E'否', E'现金', 15.00, 3)
ON CONFLICT (id) DO UPDATE SET visit_state = 3, real_name = EXCLUDED.real_name;

INSERT INTO medical_record (id, register_id, diagnosis, preliminary_diagnosis) VALUES
    (5004, 1004, E'偏头痛', E'偏头痛'),
    (5005, 1005, E'肺炎', E'肺炎')
ON CONFLICT (id) DO NOTHING;

INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5004, id FROM disease WHERE diseaseicd = 'G43.9' ON CONFLICT DO NOTHING;
INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5005, id FROM disease WHERE diseaseicd = 'J18.9' ON CONFLICT DO NOTHING;

-- 辅助：按周生成日期序列（近 13 周，含今天）
CREATE TEMP TABLE tmp_outcome_weeks AS
SELECT
    gs::date AS record_date,
    (gs::timestamp + TIME '09:30:00') AS recorded_at,
    ROW_NUMBER() OVER (ORDER BY gs) AS week_no
FROM generate_series(CURRENT_DATE - INTERVAL '84 days', CURRENT_DATE, INTERVAL '7 days') AS gs;

-- 1001 上呼吸道感染：全套呼吸 + 常规指标
INSERT INTO follow_up_health_metric (register_id, record_date, recorded_at, metric_key, metric_value, unit)
SELECT
    1001,
    w.record_date,
    w.recorded_at + ((w.week_no % 5) * INTERVAL '18 minutes'),
    m.key,
    m.val,
    m.unit
FROM tmp_outcome_weeks w
CROSS JOIN LATERAL (
    VALUES
        ('spo2',                (94 + w.week_no * 0.35)::numeric, '%'),
        ('cough_score',         GREATEST(2, 8 - w.week_no * 0.45)::numeric, E'分'),
        ('body_temperature',    (37.8 - w.week_no * 0.08)::numeric, E'℃'),
        ('blood_pressure_systolic',  (128 - w.week_no * 0.7)::numeric, 'mmHg'),
        ('blood_pressure_diastolic', (82 - w.week_no * 0.4)::numeric, 'mmHg'),
        ('blood_glucose',       (5.9 - w.week_no * 0.05)::numeric, 'mmol/L'),
        ('heart_rate',          (88 - w.week_no * 1.1)::numeric, E'次/分'),
        ('body_weight',         (72.8 - w.week_no * 0.05)::numeric, 'kg'),
        ('symptom_score',       GREATEST(1, 7 - w.week_no * 0.45)::numeric, E'分')
) AS m(key, val, unit);

-- 1002 偏头痛：神经 + 常规
INSERT INTO follow_up_health_metric (register_id, record_date, recorded_at, metric_key, metric_value, unit)
SELECT
    1002,
    w.record_date,
    w.recorded_at + ((w.week_no % 4) * INTERVAL '22 minutes'),
    m.key,
    m.val,
    m.unit
FROM tmp_outcome_weeks w
CROSS JOIN LATERAL (
    VALUES
        ('headache_score',      GREATEST(2, 8.5 - w.week_no * 0.5)::numeric, E'分'),
        ('attack_frequency',    GREATEST(1, 6 - w.week_no * 0.4)::numeric, E'次/周'),
        ('blood_pressure_systolic',  (132 - w.week_no * 0.9)::numeric, 'mmHg'),
        ('blood_pressure_diastolic', (86 - w.week_no * 0.5)::numeric, 'mmHg'),
        ('heart_rate',          (80 - w.week_no * 0.7)::numeric, E'次/分'),
        ('symptom_score',       GREATEST(1, 7.5 - w.week_no * 0.48)::numeric, E'分')
) AS m(key, val, unit);

-- 1003 肺炎
INSERT INTO follow_up_health_metric (register_id, record_date, recorded_at, metric_key, metric_value, unit)
SELECT
    1003,
    w.record_date,
    w.recorded_at + ((w.week_no % 6) * INTERVAL '15 minutes'),
    m.key,
    m.val,
    m.unit
FROM tmp_outcome_weeks w
CROSS JOIN LATERAL (
    VALUES
        ('spo2',                (91 + w.week_no * 0.5)::numeric, '%'),
        ('cough_score',         GREATEST(2, 9 - w.week_no * 0.5)::numeric, E'分'),
        ('body_temperature',    (38.1 - w.week_no * 0.1)::numeric, E'℃'),
        ('blood_pressure_systolic',  (130 - w.week_no * 0.6)::numeric, 'mmHg'),
        ('blood_pressure_diastolic', (84 - w.week_no * 0.35)::numeric, 'mmHg'),
        ('blood_glucose',       (6.3 - w.week_no * 0.06)::numeric, 'mmol/L'),
        ('heart_rate',          (90 - w.week_no * 1.0)::numeric, E'次/分'),
        ('body_weight',         (69.5 - w.week_no * 0.08)::numeric, 'kg'),
        ('symptom_score',       GREATEST(1, 8 - w.week_no * 0.5)::numeric, E'分')
) AS m(key, val, unit);

-- 1004 赵六 偏头痛（数据略波动）
INSERT INTO follow_up_health_metric (register_id, record_date, recorded_at, metric_key, metric_value, unit)
SELECT
    1004,
    w.record_date,
    w.recorded_at + TIME '10:15:00',
    m.key,
    m.val,
    m.unit
FROM tmp_outcome_weeks w
CROSS JOIN LATERAL (
    VALUES
        ('headache_score',      GREATEST(2, 7.8 - w.week_no * 0.42 + (w.week_no % 3) * 0.2)::numeric, E'分'),
        ('attack_frequency',    GREATEST(1, 5.5 - w.week_no * 0.35)::numeric, E'次/周'),
        ('blood_pressure_systolic',  (128 - w.week_no * 0.5)::numeric, 'mmHg'),
        ('blood_pressure_diastolic', (83 - w.week_no * 0.3)::numeric, 'mmHg'),
        ('heart_rate',          (76 - w.week_no * 0.5)::numeric, E'次/分'),
        ('symptom_score',       GREATEST(1, 6.8 - w.week_no * 0.4)::numeric, E'分')
) AS m(key, val, unit);

-- 1005 孙七 肺炎
INSERT INTO follow_up_health_metric (register_id, record_date, recorded_at, metric_key, metric_value, unit)
SELECT
    1005,
    w.record_date,
    w.recorded_at + TIME '11:00:00',
    m.key,
    m.val,
    m.unit
FROM tmp_outcome_weeks w
CROSS JOIN LATERAL (
    VALUES
        ('spo2',                (90 + w.week_no * 0.55)::numeric, '%'),
        ('cough_score',         GREATEST(2, 8.5 - w.week_no * 0.48)::numeric, E'分'),
        ('body_temperature',    (37.9 - w.week_no * 0.09)::numeric, E'℃'),
        ('blood_pressure_systolic',  (135 - w.week_no * 0.8)::numeric, 'mmHg'),
        ('blood_pressure_diastolic', (88 - w.week_no * 0.45)::numeric, 'mmHg'),
        ('blood_glucose',       (6.5 - w.week_no * 0.07)::numeric, 'mmol/L'),
        ('heart_rate',          (85 - w.week_no * 0.9)::numeric, E'次/分'),
        ('body_weight',         (71.2 - w.week_no * 0.06)::numeric, 'kg'),
        ('symptom_score',       GREATEST(1, 7.5 - w.week_no * 0.46)::numeric, E'分')
) AS m(key, val, unit);

DROP TABLE tmp_outcome_weeks;

INSERT INTO ai_follow_up_plan (id, register_id, follow_up_day, planned_date, follow_up_type, plan_status) VALUES
    (9001, 1001, 7,  CURRENT_DATE - 21, 'recovery', 'completed'),
    (9002, 1001, 14, CURRENT_DATE - 7,  'recovery', 'completed'),
    (9003, 1002, 7,  CURRENT_DATE - 14, 'recovery', 'completed'),
    (9004, 1003, 7,  CURRENT_DATE - 7,  'recovery', 'completed'),
    (9005, 1004, 7,  CURRENT_DATE - 14, 'recovery', 'completed'),
    (9006, 1005, 7,  CURRENT_DATE - 7,  'recovery', 'pending')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_follow_up_record (follow_up_plan_id, register_id, symptom_relief, has_side_effect, patient_feedback, ai_assessment, follow_up_time) VALUES
    (9001, 1001, 'partial',  0, E'咳嗽减轻，仍有轻微胸闷', E'呼吸道症状部分缓解', CURRENT_TIMESTAMP - INTERVAL '21 days'),
    (9002, 1001, 'relieved', 0, E'咳嗽明显减少，活动耐量提升', E'治疗效果良好，建议继续观察', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    (9003, 1002, 'partial',  0, E'头痛发作减少', E'头痛频次下降，继续当前方案', CURRENT_TIMESTAMP - INTERVAL '14 days'),
    (9004, 1003, 'partial',  0, E'体温恢复正常，咳嗽减少', E'肺炎恢复中，血氧改善', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    (9005, 1004, 'unchanged',0, E'头痛仍有反复', E'需调整随访频率', CURRENT_TIMESTAMP - INTERVAL '10 days'),
    (9006, 1005, 'partial',  1, E'偶有胸闷', E'整体好转，关注副作用', CURRENT_TIMESTAMP - INTERVAL '3 days');

SELECT setval(pg_get_serial_sequence('register', 'id'), COALESCE((SELECT MAX(id) FROM register), 1), true);
SELECT setval(pg_get_serial_sequence('medical_record', 'id'), COALESCE((SELECT MAX(id) FROM medical_record), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_health_metric', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_health_metric), 1), true);
SELECT setval(pg_get_serial_sequence('ai_follow_up_plan', 'id'), COALESCE((SELECT MAX(id) FROM ai_follow_up_plan), 1), true);
