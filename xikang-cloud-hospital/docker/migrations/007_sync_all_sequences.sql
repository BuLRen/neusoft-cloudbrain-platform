-- =============================================================================
-- 全库 SERIAL/BIGSERIAL 序列同步（迁远程 / 导入快照后必跑）
--
-- 问题：INSERT 显式指定 id、COPY、pg_dump 导入数据时，序列不会自动前进，
--       导致应用 INSERT 不写 id 时 nextval() 生成已存在的主键 → DuplicateKeyException
--
-- 作用：扫描 public 下所有带 nextval 默认值的列，将序列对齐到 MAX(id)
-- 特性：幂等，可重复执行；新增表只要用 SERIAL 会自动纳入，无需改本脚本
--
-- 建议执行时机：
--   1. 任何迁远程操作之后（快照、种子 SQL、增量迁移）
--   2. 本地 Docker 导入 init.sql / 快照之后（若未走 init.sql 末尾的手动 setval）
-- =============================================================================

DO $$
DECLARE
    rec RECORD;
    seq_name TEXT;
    max_val  BIGINT;
    synced   INT := 0;
BEGIN
    FOR rec IN
        SELECT
            n.nspname  AS schema_name,
            c.relname  AS table_name,
            a.attname  AS column_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        JOIN pg_attrdef ad ON ad.adrelid = c.oid AND ad.adnum = a.attnum
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
          AND a.attnum > 0
          AND NOT a.attisdropped
          AND pg_get_expr(ad.adbin, ad.adrelid) LIKE 'nextval(%'
        ORDER BY c.relname, a.attname
    LOOP
        seq_name := pg_get_serial_sequence(
            format('%I.%I', rec.schema_name, rec.table_name),
            rec.column_name
        );

        IF seq_name IS NULL THEN
            RAISE NOTICE '跳过 %.%（列 %）：未找到关联序列',
                rec.table_name, rec.column_name, rec.column_name;
            CONTINUE;
        END IF;

        EXECUTE format(
            'SELECT COALESCE(MAX(%I), 0) FROM %I.%I',
            rec.column_name, rec.schema_name, rec.table_name
        ) INTO max_val;

        IF max_val = 0 THEN
            -- 空表：下次 INSERT 从 1 开始
            PERFORM setval(seq_name::regclass, 1, false);
        ELSE
            -- 有数据：下次 INSERT 从 max_val + 1 开始
            PERFORM setval(seq_name::regclass, max_val, true);
        END IF;

        synced := synced + 1;
        RAISE NOTICE '已同步 % → %.% (MAX(%) = %)',
            seq_name, rec.table_name, rec.column_name, rec.column_name, max_val;
    END LOOP;

    RAISE NOTICE '序列同步完成，共处理 % 个序列', synced;
END $$;
