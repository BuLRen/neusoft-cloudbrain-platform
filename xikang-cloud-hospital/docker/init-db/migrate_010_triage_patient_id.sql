-- ============================================================
-- migrate_010_triage_patient_id.sql
-- 给 ai_triage_record 表增加 patient_id 字段，用于"导诊 → 预问诊"上下文串联
-- ============================================================
-- 背景：
--   ai_triage_record 原本只有 patient_name，没有 patient_id。
--   导诊发生在挂号之前（register_id 此时为 NULL），挂号成功后需要按 patient_id
--   回填 register_id 到该患者最近的导诊记录，预问诊才能据此拉取导诊上下文。
--
-- 执行方式（远程宝塔 PG，手动）：
--   psql -h 43.139.102.203 -U xikang_hospital -d xikang_hospital -f migrate_010_triage_patient_id.sql
--   或在宝塔面板的 phpPgAdmin / SQL 执行框里运行。
-- ============================================================

BEGIN;

-- 1. 加列（可空，兼容旧数据；旧记录 patient_id 保持 NULL）
ALTER TABLE ai_triage_record
    ADD COLUMN IF NOT EXISTS patient_id BIGINT;

-- 2. 索引：加速"按 patient_id 查最近一条 register_id IS NULL 的导诊记录"（挂号回填用）
CREATE INDEX IF NOT EXISTS idx_ai_triage_record_patient_id
    ON ai_triage_record (patient_id);

-- 3. 复合索引：加速回填时的定位查询（patient_id + register_id 是否为空 + 时间倒序）
CREATE INDEX IF NOT EXISTS idx_ai_triage_record_patient_register
    ON ai_triage_record (patient_id, register_id, triage_time DESC);

COMMIT;

-- 验证
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'ai_triage_record' AND column_name = 'patient_id';
