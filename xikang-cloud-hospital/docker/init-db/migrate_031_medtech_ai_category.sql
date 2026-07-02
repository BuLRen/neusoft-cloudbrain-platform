-- 为医技项目补全 ai_category_code，确保 CT 跳转按元数据分类而非名称猜测

UPDATE medical_technology
SET ai_category_code = 'imaging_ct_chest'
WHERE tech_code = 'XJCT'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '');

UPDATE medical_technology
SET ai_category_code = 'imaging_ct_brain'
WHERE tech_code = 'TLCT'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '');

UPDATE medical_technology
SET ai_category_code = 'imaging_ct_brain'
WHERE tech_type = 'check'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '')
  AND (
    tech_name ILIKE '%脑部%CT%'
    OR tech_name ILIKE '%头颅%CT%'
    OR tech_name ILIKE '%CT%脑%'
    OR tech_name ILIKE '%CT%颅%'
    OR tech_name ILIKE '%颅%CT%'
  );

UPDATE medical_technology
SET ai_category_code = 'imaging_ct_chest'
WHERE tech_type = 'check'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '')
  AND (
    tech_name ILIKE '%胸部%CT%'
    OR tech_name ILIKE '%CT%胸%'
    OR tech_name ILIKE '%肺%CT%'
  );

UPDATE medical_technology
SET ai_category_code = 'general_lab'
WHERE tech_type = 'inspection'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '');

UPDATE medical_technology
SET ai_category_code = 'general_check'
WHERE tech_type = 'check'
  AND (ai_category_code IS NULL OR TRIM(ai_category_code) = '');
