-- ============================================================
-- migrate_014_follow_up_outcome.sql
-- 用途：随访系统「疗效评估」页所需表结构 + 演示数据
-- 适用：已有云端/远程 xikang_hospital 库（增量执行，可重复运行）
-- 执行方式：见 docker/migrations/README.md 或交给 DBA 在 SQL 客户端中运行本文件
-- ============================================================

-- 1. 建表（已存在则跳过）
CREATE TABLE IF NOT EXISTS follow_up_health_metric (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL REFERENCES register(id),
    record_date         DATE            NOT NULL,
    metric_key          VARCHAR(64)     NOT NULL,
    metric_value        NUMERIC(10, 2)  NOT NULL,
    unit                VARCHAR(16)     DEFAULT NULL,
    source              VARCHAR(16)     NOT NULL DEFAULT 'simulated',
    note                TEXT            DEFAULT NULL,
    CONSTRAINT uk_followup_metric UNIQUE (register_id, record_date, metric_key)
);

CREATE INDEX IF NOT EXISTS idx_followup_metric_register_date
    ON follow_up_health_metric(register_id, record_date);

COMMENT ON TABLE follow_up_health_metric IS '随访疗效评估模拟健康指标表（EAV）';

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

CREATE INDEX IF NOT EXISTS idx_followup_interview_week
    ON follow_up_interview_schedule(week_start_date);

COMMENT ON TABLE follow_up_interview_schedule IS '随访每周患者访谈日程表';

-- 2. 演示数据（依赖 register / disease 等基础表；冲突则跳过）
UPDATE register SET visit_state = 3 WHERE id IN (1001, 1002);

INSERT INTO register (
    id, case_number, real_name, gender, birthdate, age, age_type, home_address,
    visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
    is_book, regist_method, regist_money, visit_state
) VALUES
    (1003, 'BL20260523003', '王五', '男', '1975-03-10', 51, '年', '沈阳市皇姑区XX大道',
     CURRENT_TIMESTAMP, '下午', 1, 1, 2, 1, '否', '医保', 15.00, 3)
ON CONFLICT (id) DO UPDATE SET visit_state = EXCLUDED.visit_state;

INSERT INTO medical_record (id, register_id, diagnosis, preliminary_diagnosis) VALUES
    (5001, 1001, '上呼吸道感染', '上呼吸道感染'),
    (5002, 1002, '偏头痛', '偏头痛'),
    (5003, 1003, '肺炎', '肺炎')
ON CONFLICT (id) DO NOTHING;

INSERT INTO medical_record_disease (medical_record_id, disease_id) VALUES
    (5001, 2), (5002, 1), (5003, 3)
ON CONFLICT DO NOTHING;

INSERT INTO follow_up_health_metric (register_id, record_date, metric_key, metric_value, unit) VALUES
    (1001, CURRENT_DATE - 28, 'spo2', 94, '%'), (1001, CURRENT_DATE - 28, 'cough_score', 7, '分'), (1001, CURRENT_DATE - 28, 'body_temperature', 37.8, '℃'),
    (1001, CURRENT_DATE - 14, 'spo2', 96, '%'), (1001, CURRENT_DATE - 14, 'cough_score', 5, '分'), (1001, CURRENT_DATE - 14, 'body_temperature', 37.2, '℃'),
    (1001, CURRENT_DATE, 'spo2', 98, '%'), (1001, CURRENT_DATE, 'cough_score', 2, '分'), (1001, CURRENT_DATE, 'body_temperature', 36.6, '℃'),
    (1001, CURRENT_DATE, 'blood_pressure_systolic', 118, 'mmHg'), (1001, CURRENT_DATE, 'blood_pressure_diastolic', 76, 'mmHg'),
    (1001, CURRENT_DATE, 'blood_glucose', 5.2, 'mmol/L'), (1001, CURRENT_DATE, 'heart_rate', 72, '次/分'), (1001, CURRENT_DATE, 'body_weight', 72.0, 'kg'),
    (1001, CURRENT_DATE - 14, 'symptom_score', 6, '分'), (1001, CURRENT_DATE, 'symptom_score', 2, '分'),
    (1002, CURRENT_DATE - 28, 'headache_score', 8, '分'), (1002, CURRENT_DATE - 28, 'attack_frequency', 5, '次/周'),
    (1002, CURRENT_DATE - 14, 'headache_score', 5, '分'), (1002, CURRENT_DATE - 14, 'attack_frequency', 3, '次/周'),
    (1002, CURRENT_DATE, 'headache_score', 3, '分'), (1002, CURRENT_DATE, 'attack_frequency', 1, '次/周'),
    (1002, CURRENT_DATE, 'blood_pressure_systolic', 120, 'mmHg'), (1002, CURRENT_DATE, 'blood_pressure_diastolic', 78, 'mmHg'), (1002, CURRENT_DATE, 'heart_rate', 70, '次/分'),
    (1002, CURRENT_DATE - 14, 'symptom_score', 7, '分'), (1002, CURRENT_DATE, 'symptom_score', 3, '分'),
    (1003, CURRENT_DATE - 21, 'spo2', 92, '%'), (1003, CURRENT_DATE - 21, 'cough_score', 8, '分'), (1003, CURRENT_DATE - 21, 'body_temperature', 38.2, '℃'),
    (1003, CURRENT_DATE, 'spo2', 97, '%'), (1003, CURRENT_DATE, 'cough_score', 3, '分'), (1003, CURRENT_DATE, 'body_temperature', 36.7, '℃'),
    (1003, CURRENT_DATE, 'blood_pressure_systolic', 125, 'mmHg'), (1003, CURRENT_DATE, 'blood_glucose', 6.1, 'mmol/L'), (1003, CURRENT_DATE, 'heart_rate', 82, '次/分')
ON CONFLICT (register_id, record_date, metric_key) DO NOTHING;

INSERT INTO ai_follow_up_plan (id, register_id, follow_up_day, planned_date, follow_up_type, plan_status) VALUES
    (9001, 1001, 7, CURRENT_DATE - 21, 'recovery', 'completed'),
    (9002, 1001, 14, CURRENT_DATE - 7, 'recovery', 'completed'),
    (9003, 1002, 7, CURRENT_DATE - 14, 'recovery', 'completed'),
    (9004, 1003, 7, CURRENT_DATE - 7, 'recovery', 'pending')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_follow_up_record (follow_up_plan_id, register_id, symptom_relief, has_side_effect, patient_feedback, ai_assessment, follow_up_time) VALUES
    (9001, 1001, 'partial', 0, '咳嗽有所减轻，仍有低热', '症状部分缓解，建议继续观察呼吸道指标', CURRENT_TIMESTAMP - INTERVAL '21 days'),
    (9002, 1001, 'relieved', 0, '咳嗽明显减少，体温正常', '呼吸道症状明显改善，治疗效果良好', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    (9003, 1002, 'partial', 0, '头痛发作减少但仍有轻度不适', '头痛频次下降，继续当前治疗方案', CURRENT_TIMESTAMP - INTERVAL '14 days')
ON CONFLICT DO NOTHING;

-- 3. 同步序列（避免后续插入主键冲突）
SELECT setval(pg_get_serial_sequence('medical_record', 'id'), COALESCE((SELECT MAX(id) FROM medical_record), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_health_metric', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_health_metric), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_interview_schedule', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_interview_schedule), 1), true);
SELECT setval(pg_get_serial_sequence('ai_follow_up_plan', 'id'), COALESCE((SELECT MAX(id) FROM ai_follow_up_plan), 1), true);
