-- ============================================================
-- 废弃旧 scheduling 表
-- 运行方式：psql -U postgres -d xikang_hospital -f drop_old_scheduling.sql
-- ============================================================

-- 删除外键约束（employee 表引用 scheduling 表）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_employee_scheduling') THEN
        ALTER TABLE employee DROP CONSTRAINT fk_employee_scheduling;
    END IF;
END$$;

-- 删除旧表
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'scheduling') THEN
        DROP TABLE IF EXISTS scheduling CASCADE;
        RAISE NOTICE '旧表 scheduling 已删除';
    ELSE
        RAISE NOTICE '旧表 scheduling 不存在，跳过';
    END IF;
END$$;

-- ============================================================
-- 清理 employee 表中的 scheduling_id 字段（可选）
-- 如果不需要保留排班规则关联，可以删除此字段
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'employee' AND column_name = 'scheduling_id') THEN
        ALTER TABLE employee DROP COLUMN scheduling_id;
        RAISE NOTICE 'employee.scheduling_id 字段已删除';
    END IF;
END$$;

-- ============================================================
-- 完成
-- ============================================================

SELECT '废弃 scheduling 表完成' AS status;