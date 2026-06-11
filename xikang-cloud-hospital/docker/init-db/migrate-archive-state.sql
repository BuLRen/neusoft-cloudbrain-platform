-- 增量迁移：医技申请增加「已归档」终态
-- 已有库可手动执行本脚本

ALTER TABLE check_request DROP CONSTRAINT IF EXISTS chk_check_request_state;
ALTER TABLE check_request ADD CONSTRAINT chk_check_request_state
    CHECK (check_state IN ('待检查', '检查中', '已完成', '已归档'));

ALTER TABLE inspection_request DROP CONSTRAINT IF EXISTS chk_inspection_state;
ALTER TABLE inspection_request ADD CONSTRAINT chk_inspection_state
    CHECK (inspection_state IN ('待检验', '检验中', '已完成', '已归档'));

ALTER TABLE disposal_request DROP CONSTRAINT IF EXISTS chk_disposal_state;
ALTER TABLE disposal_request ADD CONSTRAINT chk_disposal_state
    CHECK (disposal_state IN ('待处置', '处置中', '已完成', '已归档'));

COMMENT ON COLUMN check_request.check_state IS '状态: 待检查 → 检查中 → 已完成 / 已归档';
