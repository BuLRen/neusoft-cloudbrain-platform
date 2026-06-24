-- 导入完成后执行：修复 SERIAL/BIGSERIAL 序列，避免后续 INSERT 主键冲突
-- 可重复执行，无副作用

SET timezone = 'Asia/Shanghai';

DO $$
DECLARE
    r RECORD;
    seq_full text;
    max_val bigint;
BEGIN
    FOR r IN
        SELECT
            n.nspname AS schema_name,
            c.relname AS table_name,
            a.attname AS column_name,
            s.relname AS seq_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
        JOIN pg_depend d ON d.refobjid = c.oid AND d.refobjsubid = a.attnum AND d.deptype = 'a'
        JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S'
        WHERE n.nspname = 'public'
    LOOP
        seq_full := format('%I.%I', r.schema_name, r.seq_name);
        EXECUTE format(
            'SELECT COALESCE(MAX(%I), 0) FROM %I.%I',
            r.column_name, r.schema_name, r.table_name
        ) INTO max_val;

        IF max_val < 1 THEN
            EXECUTE format('SELECT setval(%L, 1, false)', seq_full);
        ELSE
            EXECUTE format('SELECT setval(%L, %s, true)', seq_full, max_val);
        END IF;
    END LOOP;
END $$;
