-- 005: 医技人员账号与医技项目执行科室绑定
-- 对已有库执行本脚本；新环境请同步更新 init.sql 中的相关数据

-- 1. 将医技项目的执行科室绑定到医技科室（按项目类型默认分配）
UPDATE medical_technology
SET deptment_id = 36
WHERE tech_type = 'check'
  AND (
    tech_code = 'USABD'
    OR tech_name ILIKE '%超声%'
  );

UPDATE medical_technology
SET deptment_id = 35
WHERE tech_type = 'check'
  AND (deptment_id IS NULL OR deptment_id < 35);

UPDATE medical_technology
SET deptment_id = 37
WHERE tech_type = 'inspection'
  AND (deptment_id IS NULL OR deptment_id < 35);

UPDATE medical_technology
SET deptment_id = 40
WHERE tech_type = 'disposal'
  AND (deptment_id IS NULL OR deptment_id < 35);

-- 2. 将已有 medtech01 账号绑定到放射科技师
UPDATE users
SET employee_id = (
    SELECT e.id
    FROM employee e
    WHERE e.realname = '放射科技师王一'
      AND e.deptment_id = 35
      AND e.delmark = 0
    LIMIT 1
),
real_name = COALESCE(real_name, '放射科技师王一')
WHERE username = 'medtech01'
  AND employee_id IS NULL;

-- 3. 为医技科室员工批量创建登录账号（user_type = 4）
INSERT INTO users (username, password, real_name, user_type, employee_id, status, create_time, update_time)
SELECT
    'tech_' || e.id,
    'medtech123',
    e.realname,
    4,
    e.id,
    1,
    NOW(),
    NOW()
FROM employee e
INNER JOIN department d ON d.id = e.deptment_id
WHERE d.dept_type = '医技科室'
  AND e.delmark = 0
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.employee_id = e.id
  );

SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1), true);
