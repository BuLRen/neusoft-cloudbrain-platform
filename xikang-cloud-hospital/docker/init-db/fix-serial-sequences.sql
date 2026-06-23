-- 修复 SERIAL 主键冲突：种子数据显式插入 id 后，序列仍从 1 递增会导致 duplicate key。
-- 在已有库上执行本脚本一次即可（可重复执行）。

SELECT setval(pg_get_serial_sequence('department', 'id'), COALESCE((SELECT MAX(id) FROM department), 1), true);
SELECT setval(pg_get_serial_sequence('regist_level', 'id'), COALESCE((SELECT MAX(id) FROM regist_level), 1), true);
SELECT setval(pg_get_serial_sequence('scheduling', 'id'), COALESCE((SELECT MAX(id) FROM scheduling), 1), true);
SELECT setval(pg_get_serial_sequence('settle_category', 'id'), COALESCE((SELECT MAX(id) FROM settle_category), 1), true);
SELECT setval(pg_get_serial_sequence('employee', 'id'), COALESCE((SELECT MAX(id) FROM employee), 1), true);
SELECT setval(pg_get_serial_sequence('disease', 'id'), COALESCE((SELECT MAX(id) FROM disease), 1), true);
SELECT setval(pg_get_serial_sequence('medical_technology', 'id'), COALESCE((SELECT MAX(id) FROM medical_technology), 1), true);
SELECT setval(pg_get_serial_sequence('drug_info', 'id'), COALESCE((SELECT MAX(id) FROM drug_info), 1), true);
SELECT setval(pg_get_serial_sequence('register', 'id'), COALESCE((SELECT MAX(id) FROM register), 1), true);
