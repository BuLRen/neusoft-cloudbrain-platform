-- ============================================================
-- 增量迁移：将旧库对齐至 2026-06-18 本地快照结构
-- 适用：已有 xikang_hospital 库、表已存在，但版本落后于联调分支
-- 执行前请先备份；可重复执行（使用 IF NOT EXISTS / IF EXISTS）
-- ============================================================

SET timezone = 'Asia/Shanghai';

-- ------------------------------------------------------------
-- 1. medical_technology：AI 分类字段（替代旧 check_category_id）
-- ------------------------------------------------------------
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS ai_category_code VARCHAR(64);
ALTER TABLE medical_technology DROP COLUMN IF EXISTS check_category_id;
DROP TABLE IF EXISTS check_category;

-- ------------------------------------------------------------
-- 2. medical_record：初步诊断字段
-- ------------------------------------------------------------
ALTER TABLE medical_record ADD COLUMN IF NOT EXISTS preliminary_diagnosis VARCHAR(512);

-- ------------------------------------------------------------
-- 3. ai_medical_record_log：source_type 增加 preliminary_diagnosis
-- ------------------------------------------------------------
ALTER TABLE ai_medical_record_log DROP CONSTRAINT IF EXISTS chk_ai_mrlog_source;
ALTER TABLE ai_medical_record_log ADD CONSTRAINT chk_ai_mrlog_source
    CHECK (source_type IN ('consultation', 'dictation', 'exam', 'preliminary_diagnosis'));

-- ------------------------------------------------------------
-- 4. 检查结果表单引擎（若 migrate-result-form.sql 未执行）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 5. 检查结果/检验结果字段类型（支持 JSON 动态表单）
-- ------------------------------------------------------------
ALTER TABLE check_request ALTER COLUMN check_result TYPE TEXT;
ALTER TABLE inspection_request ALTER COLUMN inspection_result TYPE TEXT;

-- ------------------------------------------------------------
-- 6. 医技申请状态：增加「已归档」
-- ------------------------------------------------------------
ALTER TABLE check_request DROP CONSTRAINT IF EXISTS chk_check_request_state;
ALTER TABLE check_request ADD CONSTRAINT chk_check_request_state
    CHECK (check_state IN ('待检查', '检查中', '已完成', '已归档'));

ALTER TABLE inspection_request DROP CONSTRAINT IF EXISTS chk_inspection_state;
ALTER TABLE inspection_request ADD CONSTRAINT chk_inspection_state
    CHECK (inspection_state IN ('待检验', '检验中', '已完成', '已归档'));

ALTER TABLE disposal_request DROP CONSTRAINT IF EXISTS chk_disposal_state;
ALTER TABLE disposal_request ADD CONSTRAINT chk_disposal_state
    CHECK (disposal_state IN ('待处置', '处置中', '已完成', '已归档'));

-- ------------------------------------------------------------
-- 7. register.visit_state：扩展 5=检查检验中, 6=检查检验完成
-- ------------------------------------------------------------
ALTER TABLE register DROP CONSTRAINT IF EXISTS chk_register_visit_state;
ALTER TABLE register ADD CONSTRAINT chk_register_visit_state CHECK (visit_state IN (1, 2, 3, 4, 5, 6));
COMMENT ON COLUMN register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号, 5-检查检验中, 6-检查检验完成';

-- ------------------------------------------------------------
-- 8. 同步 SERIAL 序列（避免 INSERT 主键冲突）
-- ------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('department', 'id'), COALESCE((SELECT MAX(id) FROM department), 1), true);
SELECT setval(pg_get_serial_sequence('regist_level', 'id'), COALESCE((SELECT MAX(id) FROM regist_level), 1), true);
SELECT setval(pg_get_serial_sequence('scheduling', 'id'), COALESCE((SELECT MAX(id) FROM scheduling), 1), true);
SELECT setval(pg_get_serial_sequence('settle_category', 'id'), COALESCE((SELECT MAX(id) FROM settle_category), 1), true);
SELECT setval(pg_get_serial_sequence('employee', 'id'), COALESCE((SELECT MAX(id) FROM employee), 1), true);
SELECT setval(pg_get_serial_sequence('disease', 'id'), COALESCE((SELECT MAX(id) FROM disease), 1), true);
SELECT setval(pg_get_serial_sequence('medical_technology', 'id'), COALESCE((SELECT MAX(id) FROM medical_technology), 1), true);
SELECT setval(pg_get_serial_sequence('result_form_field', 'id'), COALESCE((SELECT MAX(id) FROM result_form_field), 1), true);
SELECT setval(pg_get_serial_sequence('drug_info', 'id'), COALESCE((SELECT MAX(id) FROM drug_info), 1), true);
SELECT setval(pg_get_serial_sequence('register', 'id'), COALESCE((SELECT MAX(id) FROM register), 1), true);
SELECT setval(pg_get_serial_sequence('check_request', 'id'), COALESCE((SELECT MAX(id) FROM check_request), 1), true);
SELECT setval(pg_get_serial_sequence('inspection_request', 'id'), COALESCE((SELECT MAX(id) FROM inspection_request), 1), true);
SELECT setval(pg_get_serial_sequence('disposal_request', 'id'), COALESCE((SELECT MAX(id) FROM disposal_request), 1), true);
SELECT setval(pg_get_serial_sequence('prescription', 'id'), COALESCE((SELECT MAX(id) FROM prescription), 1), true);
SELECT setval(pg_get_serial_sequence('medical_record', 'id'), COALESCE((SELECT MAX(id) FROM medical_record), 1), true);
