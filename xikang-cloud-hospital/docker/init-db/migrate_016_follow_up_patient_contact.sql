-- 随访演示患者：补齐 patient 档案（手机号/身份证）以便疗效评估页查看联系方式
-- 可重复执行

INSERT INTO patient (real_name, id_card, gender, birthdate, phone, home_address, relation, is_primary, delmark) VALUES
    ('张三', '210102199005151234', '男', '1990-05-15', '13800001001', '沈阳市和平区XX路XX号', '本人', 1, 1),
    ('李四', '210102198808021234', '女', '1988-08-02', '13800001002', '沈阳市沈河区XX街', '本人', 1, 1),
    ('王五', '210102197503101234', '男', '1975-03-10', '13800001003', '沈阳市皇姑区XX大道', '本人', 1, 1),
    ('赵六', '210102198211201234', '女', '1982-11-20', '13800001004', '沈阳市铁西区', '本人', 1, 1),
    ('孙七', '210102196807081234', '男', '1968-07-08', '13800001005', '沈阳市大东区', '本人', 1, 1)
ON CONFLICT (id_card) DO UPDATE SET
    phone = EXCLUDED.phone,
    home_address = EXCLUDED.home_address,
    real_name = EXCLUDED.real_name;

UPDATE register r SET
    card_number = p.id_card,
    home_address = COALESCE(r.home_address, p.home_address)
FROM patient p
WHERE r.id BETWEEN 1001 AND 1005
  AND p.real_name = r.real_name
  AND p.birthdate = r.birthdate;
