-- =============================================================================
-- migrate_012_medication_guide.sql
-- 用药指导单（处方级）数据表
--
-- 设计：
--   - 按 register_id 聚合，一张处方一条记录
--   - guide_content 存结构化 JSON（AI 生成），下载 PDF 时实时渲染，PDF 不落盘
--   - source 标记产出方式（ai / fallback / manual），便于追溯
--   - 同一 register_id 允许多条（重试会新增），业务取最新一条
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.medication_guide (
    id              BIGSERIAL    PRIMARY KEY,
    register_id     BIGINT       NOT NULL,
    prescription_id BIGINT,
    patient_id      BIGINT,
    patient_name    VARCHAR(64),
    guide_content   JSONB        NOT NULL,
    source          VARCHAR(16)  NOT NULL DEFAULT 'ai',
    status          VARCHAR(16)  NOT NULL DEFAULT 'success',
    error_message   TEXT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_medication_guide_register     ON public.medication_guide(register_id);
CREATE INDEX IF NOT EXISTS idx_medication_guide_prescription ON public.medication_guide(prescription_id);
CREATE INDEX IF NOT EXISTS idx_medication_guide_patient      ON public.medication_guide(patient_id);

COMMENT ON TABLE  public.medication_guide IS '处方级用药指导单（AI 生成 / 降级拼接，按挂号聚合）';
COMMENT ON COLUMN public.medication_guide.guide_content IS '结构化指导内容 JSONB：items/generalAdvice/interactionsNote';
COMMENT ON COLUMN public.medication_guide.source       IS '产出方式：ai / fallback / manual';
COMMENT ON COLUMN public.medication_guide.status       IS 'success / failed（AI 失败且无降级内容）';
