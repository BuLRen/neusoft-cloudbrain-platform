-- 随访疗效评估演示数据（可单独对已有库执行）
-- 依赖：register 1001/1002、disease 1-3

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
    (5001, 2),
    (5002, 1),
    (5003, 3)
ON CONFLICT DO NOTHING;

-- 呼吸系统患者 1001：血氧/咳嗽/体温改善
INSERT INTO follow_up_health_metric (register_id, record_date, metric_key, metric_value, unit) VALUES
    (1001, CURRENT_DATE - 28, 'spo2', 94, '%'),
    (1001, CURRENT_DATE - 28, 'cough_score', 7, '分'),
    (1001, CURRENT_DATE - 28, 'body_temperature', 37.8, '℃'),
    (1001, CURRENT_DATE - 21, 'spo2', 95, '%'),
    (1001, CURRENT_DATE - 21, 'cough_score', 6, '分'),
    (1001, CURRENT_DATE - 21, 'body_temperature', 37.5, '℃'),
    (1001, CURRENT_DATE - 14, 'spo2', 96, '%'),
    (1001, CURRENT_DATE - 14, 'cough_score', 5, '分'),
    (1001, CURRENT_DATE - 14, 'body_temperature', 37.2, '℃'),
    (1001, CURRENT_DATE - 7, 'spo2', 97, '%'),
    (1001, CURRENT_DATE - 7, 'cough_score', 3, '分'),
    (1001, CURRENT_DATE - 7, 'body_temperature', 36.9, '℃'),
    (1001, CURRENT_DATE, 'spo2', 98, '%'),
    (1001, CURRENT_DATE, 'cough_score', 2, '分'),
    (1001, CURRENT_DATE, 'body_temperature', 36.6, '℃'),
    (1001, CURRENT_DATE - 28, 'blood_pressure_systolic', 128, 'mmHg'),
    (1001, CURRENT_DATE - 28, 'blood_pressure_diastolic', 82, 'mmHg'),
    (1001, CURRENT_DATE - 28, 'blood_glucose', 5.8, 'mmol/L'),
    (1001, CURRENT_DATE - 28, 'heart_rate', 88, '次/分'),
    (1001, CURRENT_DATE - 28, 'body_weight', 72.5, 'kg'),
    (1001, CURRENT_DATE, 'blood_pressure_systolic', 118, 'mmHg'),
    (1001, CURRENT_DATE, 'blood_pressure_diastolic', 76, 'mmHg'),
    (1001, CURRENT_DATE, 'blood_glucose', 5.2, 'mmol/L'),
    (1001, CURRENT_DATE, 'heart_rate', 72, '次/分'),
    (1001, CURRENT_DATE, 'body_weight', 72.0, 'kg'),
    (1001, CURRENT_DATE - 14, 'symptom_score', 6, '分'),
    (1001, CURRENT_DATE, 'symptom_score', 2, '分')
ON CONFLICT (register_id, record_date, metric_key) DO NOTHING;

-- 神经系统患者 1002：头痛评分/发作频次下降
INSERT INTO follow_up_health_metric (register_id, record_date, metric_key, metric_value, unit) VALUES
    (1002, CURRENT_DATE - 28, 'headache_score', 8, '分'),
    (1002, CURRENT_DATE - 28, 'attack_frequency', 5, '次/周'),
    (1002, CURRENT_DATE - 21, 'headache_score', 7, '分'),
    (1002, CURRENT_DATE - 21, 'attack_frequency', 4, '次/周'),
    (1002, CURRENT_DATE - 14, 'headache_score', 5, '分'),
    (1002, CURRENT_DATE - 14, 'attack_frequency', 3, '次/周'),
    (1002, CURRENT_DATE - 7, 'headache_score', 4, '分'),
    (1002, CURRENT_DATE - 7, 'attack_frequency', 2, '次/周'),
    (1002, CURRENT_DATE, 'headache_score', 3, '分'),
    (1002, CURRENT_DATE, 'attack_frequency', 1, '次/周'),
    (1002, CURRENT_DATE - 28, 'blood_pressure_systolic', 132, 'mmHg'),
    (1002, CURRENT_DATE - 28, 'blood_pressure_diastolic', 85, 'mmHg'),
    (1002, CURRENT_DATE - 28, 'heart_rate', 78, '次/分'),
    (1002, CURRENT_DATE, 'blood_pressure_systolic', 120, 'mmHg'),
    (1002, CURRENT_DATE, 'blood_pressure_diastolic', 78, 'mmHg'),
    (1002, CURRENT_DATE, 'heart_rate', 70, '次/分'),
    (1002, CURRENT_DATE - 14, 'symptom_score', 7, '分'),
    (1002, CURRENT_DATE, 'symptom_score', 3, '分')
ON CONFLICT (register_id, record_date, metric_key) DO NOTHING;

-- 肺炎患者 1003
INSERT INTO follow_up_health_metric (register_id, record_date, metric_key, metric_value, unit) VALUES
    (1003, CURRENT_DATE - 21, 'spo2', 92, '%'),
    (1003, CURRENT_DATE - 21, 'cough_score', 8, '分'),
    (1003, CURRENT_DATE - 21, 'body_temperature', 38.2, '℃'),
    (1003, CURRENT_DATE - 14, 'spo2', 94, '%'),
    (1003, CURRENT_DATE - 14, 'cough_score', 6, '分'),
    (1003, CURRENT_DATE - 14, 'body_temperature', 37.6, '℃'),
    (1003, CURRENT_DATE - 7, 'spo2', 96, '%'),
    (1003, CURRENT_DATE - 7, 'cough_score', 4, '分'),
    (1003, CURRENT_DATE - 7, 'body_temperature', 37.0, '℃'),
    (1003, CURRENT_DATE, 'spo2', 97, '%'),
    (1003, CURRENT_DATE, 'cough_score', 3, '分'),
    (1003, CURRENT_DATE, 'body_temperature', 36.7, '℃'),
    (1003, CURRENT_DATE, 'blood_pressure_systolic', 125, 'mmHg'),
    (1003, CURRENT_DATE, 'blood_pressure_diastolic', 80, 'mmHg'),
    (1003, CURRENT_DATE, 'blood_glucose', 6.1, 'mmol/L'),
    (1003, CURRENT_DATE, 'heart_rate', 82, '次/分'),
    (1003, CURRENT_DATE, 'body_weight', 68.0, 'kg')
ON CONFLICT (register_id, record_date, metric_key) DO NOTHING;

INSERT INTO ai_follow_up_plan (id, register_id, follow_up_day, planned_date, follow_up_type, plan_status) VALUES
    (9001, 1001, 7, CURRENT_DATE - 21, 'recovery', 'completed'),
    (9002, 1001, 14, CURRENT_DATE - 7, 'recovery', 'completed'),
    (9003, 1002, 7, CURRENT_DATE - 14, 'recovery', 'completed'),
    (9004, 1003, 7, CURRENT_DATE - 7, 'recovery', 'pending')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_follow_up_record (
    follow_up_plan_id, register_id, symptom_relief, has_side_effect, patient_feedback, ai_assessment, follow_up_time
) VALUES
    (9001, 1001, 'partial', 0, '咳嗽有所减轻，仍有低热', '症状部分缓解，建议继续观察呼吸道指标', CURRENT_TIMESTAMP - INTERVAL '21 days'),
    (9002, 1001, 'relieved', 0, '咳嗽明显减少，体温正常', '呼吸道症状明显改善，治疗效果良好', CURRENT_TIMESTAMP - INTERVAL '7 days'),
    (9003, 1002, 'partial', 0, '头痛发作减少但仍有轻度不适', '头痛频次下降，继续当前治疗方案', CURRENT_TIMESTAMP - INTERVAL '14 days')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('medical_record', 'id'), COALESCE((SELECT MAX(id) FROM medical_record), 1), true);
SELECT setval(pg_get_serial_sequence('follow_up_health_metric', 'id'), COALESCE((SELECT MAX(id) FROM follow_up_health_metric), 1), true);
SELECT setval(pg_get_serial_sequence('ai_follow_up_plan', 'id'), COALESCE((SELECT MAX(id) FROM ai_follow_up_plan), 1), true);
