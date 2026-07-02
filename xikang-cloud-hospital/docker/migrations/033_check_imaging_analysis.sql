-- migrate_033: 检查单 CT 伪影分析结果持久化
-- 注意：请整段执行，不要只选中 COMMENT 语句单独运行

ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_analysis_result TEXT;
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS imaging_analyzed_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'check_request'
          AND column_name = 'imaging_analysis_result'
    ) THEN
        EXECUTE 'COMMENT ON COLUMN check_request.imaging_analysis_result IS ''CT 伪影分析结果 JSON（ai-ct-service）''';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'check_request'
          AND column_name = 'imaging_analyzed_at'
    ) THEN
        EXECUTE 'COMMENT ON COLUMN check_request.imaging_analyzed_at IS ''CT 伪影分析完成时间''';
    END IF;
END $$;
