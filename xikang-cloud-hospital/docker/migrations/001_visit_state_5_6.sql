-- Extend register.visit_state to support exam workflow states 5 and 6.
ALTER TABLE register DROP CONSTRAINT IF EXISTS chk_register_visit_state;
ALTER TABLE register ADD CONSTRAINT chk_register_visit_state CHECK (visit_state IN (1, 2, 3, 4, 5, 6));
COMMENT ON COLUMN register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号, 5-检查检验中, 6-检查检验完成';
