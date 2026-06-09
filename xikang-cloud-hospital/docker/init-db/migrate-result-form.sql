-- 增量迁移：检查结果可配置表单引擎
CREATE TABLE IF NOT EXISTS result_form_category (
    category_code   VARCHAR(64)     PRIMARY KEY,
    category_name   VARCHAR(128)    NOT NULL,
    description     VARCHAR(512)    DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS result_form_field (
    id              SERIAL          PRIMARY KEY,
    owner_type      VARCHAR(32)     NOT NULL,
    owner_key       VARCHAR(64)     NOT NULL,
    field_key       VARCHAR(64)     NOT NULL,
    field_label     VARCHAR(128)    NOT NULL,
    field_type      VARCHAR(32)     NOT NULL DEFAULT 'textarea',
    required        BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    placeholder     VARCHAR(255)    DEFAULT NULL,
    max_length      INTEGER         DEFAULT NULL,
    options_json    TEXT            DEFAULT NULL,
    CONSTRAINT uk_result_form_field_owner_key UNIQUE (owner_type, owner_key, field_key),
    CONSTRAINT chk_result_form_field_owner_type CHECK (owner_type IN ('category', 'tech_extension')),
    CONSTRAINT chk_result_form_field_type CHECK (field_type IN ('text', 'textarea', 'number'))
);

CREATE INDEX IF NOT EXISTS idx_result_form_field_owner ON result_form_field(owner_type, owner_key);

INSERT INTO result_form_category (category_code, category_name, description) VALUES
    ('general_check', '通用检查', '普通检查项目的默认结果录入模板'),
    ('imaging_ct', '影像CT', 'CT 影像检查结构化报告模板')
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO result_form_field (owner_type, owner_key, field_key, field_label, field_type, required, sort_order, placeholder) VALUES
    ('category', 'general_check', 'checkResult', '检查结果', 'textarea', TRUE, 1, '请填写检查结果'),
    ('category', 'general_check', 'checkRemark', '备注', 'textarea', FALSE, 2, '可选备注'),
    ('category', 'imaging_ct', 'findings', '所见', 'textarea', TRUE, 1, '影像所见描述'),
    ('category', 'imaging_ct', 'impression', '印象', 'textarea', TRUE, 2, '影像印象'),
    ('category', 'imaging_ct', 'conclusion', '结论', 'textarea', TRUE, 3, '诊断结论'),
    ('tech_extension', '1', 'contrastReaction', '造影剂反应', 'text', FALSE, 10, '如：无、轻微恶心等')
ON CONFLICT (owner_type, owner_key, field_key) DO NOTHING;

UPDATE medical_technology SET ai_category_code = 'imaging_ct_chest' WHERE tech_code = 'XJCT' AND (ai_category_code IS NULL OR ai_category_code = '');
UPDATE medical_technology SET ai_category_code = 'imaging_ct_brain' WHERE tech_code = 'TLCT' AND (ai_category_code IS NULL OR ai_category_code = '');

SELECT setval(pg_get_serial_sequence('result_form_field', 'id'), COALESCE((SELECT MAX(id) FROM result_form_field), 1), true);
