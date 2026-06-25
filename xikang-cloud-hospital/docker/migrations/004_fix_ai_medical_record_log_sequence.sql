-- =============================================================================
-- 修复 ai_medical_record_log 主键序列与表中 MAX(id) 不一致
--
-- 背景：xikang_hospital_snapshot_20260624.sql 种子数据显式插入了 id=8/9，
-- 但 setval 仍停在 7，导致 INSERT 时 nextval 生成已存在的 id，报：
--   duplicate key value violates unique constraint "ai_medical_record_log_pkey"
--
-- 可重复执行（幂等）。
-- =============================================================================

SELECT setval(
    pg_get_serial_sequence('ai_medical_record_log', 'id'),
    COALESCE((SELECT MAX(id) FROM ai_medical_record_log), 1),
    true
);
