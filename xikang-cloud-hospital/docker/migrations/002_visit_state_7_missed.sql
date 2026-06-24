-- Extend register.visit_state to support 7 = 爽约 (no-show).
-- 5/6 remain 检验中 / 检验完成（医生端状态机）。爽约独立为 7，避免和检验流程冲突。
ALTER TABLE register DROP CONSTRAINT IF EXISTS chk_register_visit_state;
ALTER TABLE register ADD CONSTRAINT chk_register_visit_state CHECK (visit_state IN (1, 2, 3, 4, 5, 6, 7));
COMMENT ON COLUMN register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号, 5-检查检验中, 6-检查检验完成, 7-爽约';

-- 历史数据迁移：把已被早期 scheduler 错标为 5 的爽约记录修正到 7。
-- 爽约的判定：check_in_time IS NULL（从未报到）。
UPDATE register
SET visit_state = 7
WHERE visit_state = 5
  AND check_in_time IS NULL;