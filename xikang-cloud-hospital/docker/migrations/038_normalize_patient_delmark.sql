-- ============================================================
-- 038_normalize_patient_delmark.sql
-- 防御性迁移：规范化 patient.delmark 字段语义，修复历史脏数据
--
-- 背景：
--   patient 表建表时（migrate_001）delmark DEFAULT 1（1=正常）
--   后期代码语义反转成 0=正常，1=删除（PatientMapper.xml 里查 delmark=0，deleteById 设 delmark=1）
--   导致早期注册的 patient（delmark=1）被所有查询过滤掉
--   表现为：/api/auth/me 返回 patients: []，前端"患者信息加载失败"
--
-- 本次迁移做了两件事：
--   1. 把仍被 user_patient_managed 关联的 patient（即未真正删除的有效档案）的 delmark 翻成 0
--      ——只动"被关联的"，避免误恢复已被 deleteById 软删的档案
--   2. 把表默认值改成 0，避免未来直接 SQL INSERT 时再次踩坑
--
-- 运行方式：
--   psql -h <host> -p <port> -U <user> -d xikang_hospital -f 038_normalize_patient_delmark.sql
--
-- 注意：本脚本幂等，可重复执行。2026-07-07 已在远程库手工执行过同样的 UPDATE，再跑也不会有副作用。
-- ============================================================

BEGIN;

-- ============================================================
-- 步骤 1：修复历史脏数据
-- 规则：只要 patient 还被任意 user_patient_managed 关联，就视为有效档案
--       （因为 deleteById 不会同时删 user_patient_managed，被关联即说明没被删）
-- ============================================================

\echo '===== 修复前：delmark=1 但仍被关联的 patient 数 ====='
SELECT COUNT(*) AS conflicted_count
FROM patient
WHERE delmark = 1
  AND id IN (SELECT patient_id FROM user_patient_managed);

UPDATE patient
SET delmark = 0,
    update_time = NOW()
WHERE delmark = 1
  AND id IN (SELECT patient_id FROM user_patient_managed);

\echo '===== 修复后：再核对一次 ====='
SELECT COUNT(*) AS remaining_conflicted_count
FROM patient
WHERE delmark = 1
  AND id IN (SELECT patient_id FROM user_patient_managed);

-- ============================================================
-- 步骤 2：改默认值（如果当前是 1 才改，避免无意义 ALTER）
-- ============================================================
DO $$
DECLARE
    current_default TEXT;
BEGIN
    SELECT columns_default
    INTO current_default
    FROM (
        SELECT column_name, column_default AS columns_default
        FROM information_schema.columns
        WHERE table_name = 'patient' AND column_name = 'delmark'
    ) t;

    IF current_default IS NULL OR current_default = '1' OR current_default LIKE '%1%' THEN
        ALTER TABLE patient ALTER COLUMN delmark SET DEFAULT 0;
        RAISE NOTICE 'patient.delmark 默认值已改为 0（原值: %）', current_default;
    ELSE
        RAISE NOTICE 'patient.delmark 默认值已是 %，跳过', current_default;
    END IF;
END$$;

-- ============================================================
-- 步骤 3：最终状态报告
-- ============================================================
\echo '===== patient 表 delmark 分布 ====='
SELECT
    CASE WHEN delmark = 0 THEN '0 (正常)' ELSE '1 (已删除)' END AS delmark_state,
    COUNT(*) AS patient_count
FROM patient
GROUP BY delmark
ORDER BY delmark;

COMMIT;

\echo '===== 迁移完成 =====';
