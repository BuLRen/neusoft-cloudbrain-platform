-- ============================================================
-- 迁移 003: 挂号记录绑定患者与排班
-- 说明: 支持患者端按可用排班挂号、号源扣减/退还、患者挂号历史准确查询
-- ============================================================

ALTER TABLE register
    ADD COLUMN IF NOT EXISTS patient_id BIGINT,
    ADD COLUMN IF NOT EXISTS scheduling_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_register_patient_id ON register(patient_id);
CREATE INDEX IF NOT EXISTS idx_register_scheduling_id ON register(scheduling_id);

ALTER TABLE register DROP CONSTRAINT IF EXISTS chk_register_noon;
ALTER TABLE register
    ADD CONSTRAINT chk_register_noon CHECK (noon IS NULL OR noon IN ('上午', '下午', '晚上'));

COMMENT ON COLUMN register.patient_id IS '患者ID';
COMMENT ON COLUMN register.scheduling_id IS '排班ID';
