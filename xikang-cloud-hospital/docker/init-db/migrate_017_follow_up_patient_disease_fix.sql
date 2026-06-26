-- 修复随访演示患者疾病关联（疗效评估患者列表与疾病图表依赖此数据）
-- 可重复执行

INSERT INTO medical_record_disease (medical_record_id, disease_id) VALUES
    (5001, 2),
    (5002, 1),
    (5003, 3)
ON CONFLICT DO NOTHING;

INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5004, id FROM disease WHERE diseaseicd = 'G43.9'
ON CONFLICT DO NOTHING;

INSERT INTO medical_record_disease (medical_record_id, disease_id)
SELECT 5005, id FROM disease WHERE diseaseicd = 'J18.9'
ON CONFLICT DO NOTHING;

UPDATE register SET visit_state = 3 WHERE id BETWEEN 1001 AND 1005;
