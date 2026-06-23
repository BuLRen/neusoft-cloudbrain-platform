-- migrate_003: users.employee_id + 诊疗医生批量账号
-- 对已有库执行本脚本；新环境请使用 init.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_id INTEGER DEFAULT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_employee'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_employee
            FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_employee_id ON users(employee_id);

COMMENT ON COLUMN users.employee_id IS '关联的员工档案ID（诊疗医生/医技人员等）';

UPDATE users SET employee_id = 1 WHERE username = 'doctor1' AND employee_id IS NULL;

INSERT INTO users (username, password, real_name, user_type, employee_id, status)
SELECT
    'doc_' || e.id,
    'doctor123',
    e.realname,
    2,
    e.id,
    1
FROM employee e
WHERE e.deptment_id BETWEEN 1 AND 20
  AND e.delmark = 0
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.employee_id = e.id
  );

SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1), true);

-- 【预留，本次不执行】检查/检验人员账号（下一阶段取消注释即可）
-- UPDATE users SET employee_id = (SELECT id FROM employee WHERE realname = '放射科技师王一' LIMIT 1)
--   WHERE username = 'medtech01';
-- INSERT INTO users (username, password, real_name, user_type, employee_id, status)
-- SELECT 'tech_' || e.id, 'medtech123', e.realname, 4, e.id, 1
-- FROM employee e
-- INNER JOIN department d ON d.id = e.deptment_id
-- WHERE d.dept_type = '医技科室' AND e.delmark = 0
--   AND NOT EXISTS (SELECT 1 FROM users u WHERE u.employee_id = e.id);
