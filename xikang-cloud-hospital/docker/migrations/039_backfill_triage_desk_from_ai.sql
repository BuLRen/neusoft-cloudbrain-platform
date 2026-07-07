-- 从 ai_triage_record 回填 triage_desk_record（仅插入尚无对应分诊台记录的导诊历史）
INSERT INTO triage_desk_record (
    patient_id,
    patient_name,
    symptoms,
    ai_triage_result,
    recommended_department_id,
    recommended_department,
    recommended_physician_id,
    recommended_physician_name,
    risk_level,
    ai_analysis,
    status,
    create_time
)
SELECT
    a.patient_id,
    COALESCE(a.patient_name, '匿名患者'),
    COALESCE(a.symptom_description, ''),
    json_build_object(
        'recommendedDepartment', a.recommend_dept_name,
        'recommendedDepartmentId', a.recommend_dept_id,
        'riskLevel', a.risk_level
    )::text,
    a.recommend_dept_id,
    a.recommend_dept_name,
    a.recommend_doctor_id,
    a.recommend_doctor_name,
    a.risk_level,
    a.ai_analysis,
    0,
    COALESCE(a.triage_time, CURRENT_TIMESTAMP)
FROM ai_triage_record a
WHERE a.patient_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM triage_desk_record t
      WHERE t.patient_id = a.patient_id
        AND t.create_time = COALESCE(a.triage_time, CURRENT_TIMESTAMP)
  );
