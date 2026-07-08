-- 员工诊室字段，供候诊大屏展示
ALTER TABLE employee ADD COLUMN IF NOT EXISTS clinic_room VARCHAR(64) NULL;
COMMENT ON COLUMN employee.clinic_room IS '诊室名称，用于候诊大屏展示';
