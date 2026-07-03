-- W2 检查推荐工作流输出持久化：ai_medical_record_log.source_type 增加 w2_recommend
-- 可重复执行（幂等）

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'public'
          AND t.relname = 'ai_medical_record_log'
          AND c.conname = 'chk_ai_mrlog_source'
          AND pg_get_constraintdef(c.oid) LIKE '%w2_recommend%'
    ) THEN
        RAISE NOTICE 'chk_ai_mrlog_source 已包含 w2_recommend，跳过迁移';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'public'
          AND t.relname = 'ai_medical_record_log'
          AND c.conname = 'chk_ai_mrlog_source'
    ) THEN
        ALTER TABLE public.ai_medical_record_log DROP CONSTRAINT chk_ai_mrlog_source;
    END IF;

    ALTER TABLE public.ai_medical_record_log ADD CONSTRAINT chk_ai_mrlog_source
        CHECK (source_type IN (
            'consultation',
            'dictation',
            'exam',
            'preliminary_diagnosis',
            'w2_recommend'
        ));
END $$;
