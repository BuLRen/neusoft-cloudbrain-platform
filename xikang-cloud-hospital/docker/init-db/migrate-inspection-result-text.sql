-- 检验结果改为 TEXT，支持 JSON 动态表单 payload
ALTER TABLE inspection_request ALTER COLUMN inspection_result TYPE TEXT;

-- 通用检验表单分类
INSERT INTO result_form_category (category_code, category_name, description) VALUES
    ('general_lab', '通用检验', '普通检验项目的默认结果录入模板')
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO result_form_field (owner_type, owner_key, field_key, field_label, field_type, required, sort_order, placeholder) VALUES
    ('category', 'general_lab', 'inspectionResult', '检验结果', 'textarea', TRUE, 1, '请填写检验结论或汇总'),
    ('category', 'general_lab', 'inspectionRemark', '备注', 'textarea', FALSE, 2, '可选备注')
ON CONFLICT (owner_type, owner_key, field_key) DO NOTHING;

-- 检验项目绑定 general_lab 分类
UPDATE medical_technology
SET ai_category_code = 'general_lab'
WHERE tech_type = 'inspection' AND (ai_category_code IS NULL OR ai_category_code = '');
