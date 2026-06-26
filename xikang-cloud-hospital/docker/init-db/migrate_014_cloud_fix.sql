-- Cloud-compatible fix for follow-up outcome demo data
-- Run via: docker cp + psql -f (avoid PowerShell piping encoding issues)

CREATE TABLE IF NOT EXISTS follow_up_interview_schedule (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL REFERENCES register(id),
    case_number         VARCHAR(64)     DEFAULT NULL,
    patient_name        VARCHAR(64)     DEFAULT NULL,
    week_start_date     DATE            NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'scheduled',
    trigger_reason      TEXT            DEFAULT NULL,
    trigger_metric_key  VARCHAR(64)     DEFAULT NULL,
    created_by          INTEGER         DEFAULT NULL,
    patient_notified    SMALLINT        DEFAULT 0,
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_followup_interview_week UNIQUE (register_id, week_start_date),
    CONSTRAINT chk_followup_interview_status CHECK (status IN ('scheduled', 'completed', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_followup_interview_week ON follow_up_interview_schedule(week_start_date);

INSERT INTO disease (id, disease_code, disease_name, diseaseicd, disease_category) VALUES
    (1, 'PJT', E'偏头痛', 'G43.9', E'神经系统疾病'),
    (2, 'SXDGR', E'上呼吸道感染', 'J06.9', E'呼吸系统疾病'),
    (3, 'FY', E'肺炎', 'J18.9', E'呼吸系统疾病')
ON CONFLICT (diseaseicd) DO NOTHING;

INSERT INTO register (
    id, case_number, real_name, gender, birthdate, age, age_type, home_address,
    visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
    is_book, regist_method, regist_money, visit_state
) VALUES
    (1001, 'BL20260523001', E'张三', E'男', '1990-05-15', 36, E'年', E'沈阳市和平区',
     CURRENT_TIMESTAMP, E'上午', 1, 1, 2, 1, E'否', E'现金', 15.00, 3),
    (1002, 'BL20260523002', E'李四', E'女', '1988-08-02', 38, E'年', E'沈阳市沈河区',
     CURRENT_TIMESTAMP, E'上午', 1, 1, 2, 1, E'否', E'微信', 15.00, 3),
    (1003, 'BL20260523003', E'王五', E'男', '1975-03-10', 51, E'年', E'沈阳市皇姑区',
     CURRENT_TIMESTAMP, E'下午', 2, 2, 2, 1, E'否', E'医保', 15.00, 3)
ON CONFLICT (id) DO UPDATE SET visit_state = 3, real_name = EXCLUDED.real_name;

INSERT INTO medical_record (id, register_id, diagnosis, preliminary_diagnosis) VALUES
    (5001, 1001, E'上呼吸道感染', E'上呼吸道感染'),
    (5002, 1002, E'偏头痛', E'偏头痛'),
    (5003, 1003, E'肺炎', E'肺炎')
ON CONFLICT (id) DO NOTHING;

INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5001, id FROM disease WHERE diseaseicd = 'J06.9' ON CONFLICT DO NOTHING;
INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5002, id FROM disease WHERE diseaseicd = 'G43.9' ON CONFLICT DO NOTHING;
INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5003, id FROM disease WHERE diseaseicd = 'J18.9' ON CONFLICT DO NOTHING;

INSERT INTO follow_up_health_metric (register_id, record_date, metric_key, metric_value, unit) VALUES
    (1001, CURRENT_DATE - 28, 'spo2', 94, '%'), (1001, CURRENT_DATE - 28, 'cough_score', 7, E'分'),
    (1001, CURRENT_DATE - 14, 'spo2', 96, '%'), (1001, CURRENT_DATE - 14, 'cough_score', 5, E'分'),
    (1001, CURRENT_DATE, 'spo2', 98, '%'), (1001, CURRENT_DATE, 'cough_score', 2, E'分'),
    (1001, CURRENT_DATE, 'blood_pressure_systolic', 118, 'mmHg'), (1001, CURRENT_DATE, 'blood_glucose', 5.2, 'mmol/L'),
    (1001, CURRENT_DATE, 'heart_rate', 72, E'次/分'), (1001, CURRENT_DATE - 14, 'symptom_score', 6, E'分'), (1001, CURRENT_DATE, 'symptom_score', 2, E'分'),
    (1002, CURRENT_DATE - 28, 'headache_score', 8, E'分'), (1002, CURRENT_DATE - 28, 'attack_frequency', 5, E'次/周'),
    (1002, CURRENT_DATE - 14, 'headache_score', 5, E'分'), (1002, CURRENT_DATE, 'headache_score', 3, E'分'),
    (1002, CURRENT_DATE, 'attack_frequency', 1, E'次/周'), (1002, CURRENT_DATE, 'heart_rate', 70, E'次/分'),
    (1003, CURRENT_DATE - 21, 'spo2', 92, '%'), (1003, CURRENT_DATE, 'spo2', 97, '%'),
    (1003, CURRENT_DATE, 'cough_score', 3, E'分'), (1003, CURRENT_DATE, 'body_temperature', 36.7, E'℃')
ON CONFLICT (register_id, record_date, metric_key) DO NOTHING;

INSERT INTO ai_follow_up_plan (id, register_id, follow_up_day, planned_date, follow_up_type, plan_status) VALUES
    (9001, 1001, 7, CURRENT_DATE - 21, 'recovery', 'completed'),
    (9002, 1001, 14, CURRENT_DATE - 7, 'recovery', 'completed'),
    (9003, 1002, 7, CURRENT_DATE - 14, 'recovery', 'completed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_follow_up_record (follow_up_plan_id, register_id, symptom_relief, has_side_effect, patient_feedback, ai_assessment, follow_up_time) VALUES
    (9001, 1001, 'partial', 0, E'咳嗽有所减轻', E'症状部分缓解', CURRENT_TIMESTAMP - INTERVAL '21 days'),
    (9002, 1001, 'relieved', 0, E'咳嗽明显减少', E'治疗效果良好', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    (9003, 1002, 'partial', 0, E'头痛发作减少', E'头痛频次下降', CURRENT_TIMESTAMP - INTERVAL '14 days');

SELECT setval(pg_get_serial_sequence('register', 'id'), COALESCE((SELECT MAX(id) FROM register), 1), true);
SELECT setval(pg_get_serial_sequence('medical_record', 'id'), COALESCE((SELECT MAX(id) FROM medical_record), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_health_metric', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_health_metric), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_interview_schedule', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_interview_schedule), 1), true);
SELECT setval(pg_get_serial_sequence('ai_follow_up_plan', 'id'), COALESCE((SELECT MAX(id) FROM ai_follow_up_plan), 1), true);
