-- ============================================================
-- 增量迁移脚本：添加患者管理相关表和字段
-- 运行方式：psql -U postgres -d xikang_hospital -f migrate_001_patient_management.sql
-- ============================================================

-- ============================================================
-- 1. 扩展 users 表（新增字段，不影响现有数据）
-- ============================================================

-- 新增字段（IF NOT EXISTS 不支持，改用 DO 块判断）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'id_card') THEN
        ALTER TABLE users ADD COLUMN id_card VARCHAR(18) DEFAULT NULL;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'gender') THEN
        ALTER TABLE users ADD COLUMN gender VARCHAR(6) DEFAULT NULL;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'birthdate') THEN
        ALTER TABLE users ADD COLUMN birthdate DATE DEFAULT NULL;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'avatar') THEN
        ALTER TABLE users ADD COLUMN avatar VARCHAR(255) DEFAULT NULL;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'patient_id') THEN
        ALTER TABLE users ADD COLUMN patient_id INTEGER DEFAULT NULL;
    END IF;
END$$;

-- 添加性别检查约束
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_name = 'users' AND constraint_name = 'chk_users_gender') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_gender CHECK (gender IS NULL OR gender IN ('男', '女'));
    END IF;
END$$;

-- 添加身份证唯一约束
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_name = 'users' AND constraint_name = 'uk_users_id_card') THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_id_card UNIQUE (id_card);
    END IF;
END$$;

-- 添加 patient_id 索引
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_id_card') THEN
        CREATE INDEX idx_users_id_card ON users(id_card);
    END IF;
END$$;

-- ============================================================
-- 2. 创建 patient 表（患者档案表）
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'patient') THEN
        CREATE TABLE patient (
            id              SERIAL          PRIMARY KEY,
            real_name       VARCHAR(64)     NOT NULL,
            id_card         VARCHAR(18)     NOT NULL,
            gender          VARCHAR(6)      NOT NULL,
            birthdate       DATE            NOT NULL,
            phone           VARCHAR(20)     DEFAULT NULL,
            avatar          VARCHAR(255)    DEFAULT NULL,
            home_address    VARCHAR(255)    DEFAULT NULL,
            allergy_history VARCHAR(512)    DEFAULT NULL,
            account_balance DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
            relation        VARCHAR(16)     DEFAULT '本人',
            is_primary      SMALLINT       NOT NULL DEFAULT 0,
            delmark         SMALLINT       NOT NULL DEFAULT 1,
            create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

            CONSTRAINT uk_patient_id_card UNIQUE (id_card),
            CONSTRAINT chk_patient_gender CHECK (gender IN ('男', '女')),
            CONSTRAINT chk_patient_relation CHECK (relation IN ('本人', '父亲', '母亲', '配偶', '子女', '祖父', '祖母', '外祖父', '外祖母', '兄弟', '姐妹', '其他')),
            CONSTRAINT chk_patient_account_balance CHECK (account_balance >= 0),
            CONSTRAINT chk_patient_primary CHECK (is_primary IN (0, 1)),
            CONSTRAINT chk_patient_delmark CHECK (delmark IN (0, 1))
        );

        CREATE INDEX idx_patient_id_card ON patient(id_card);

        COMMENT ON TABLE patient IS '患者档案表';
        COMMENT ON COLUMN patient.id_card IS '身份证号，唯一';
        COMMENT ON COLUMN patient.allergy_history IS '过敏史';
        COMMENT ON COLUMN patient.account_balance IS '患者账户余额';
        COMMENT ON COLUMN patient.relation IS '与账号本人的关系';
        COMMENT ON COLUMN patient.is_primary IS '是否为本账号本人: 1-是, 0-否(家人)';
    END IF;
END$$;

-- ============================================================
-- 3. 创建 user_patient_managed 表（用户管理患者关联表）
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_patient_managed') THEN
        CREATE TABLE user_patient_managed (
            user_id         INTEGER         NOT NULL,
            patient_id      INTEGER         NOT NULL,
            relation        VARCHAR(16)     DEFAULT '本人',
            create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

            PRIMARY KEY (user_id, patient_id),

            CONSTRAINT fk_upm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            CONSTRAINT fk_upm_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
        );

        CREATE INDEX idx_upm_user ON user_patient_managed(user_id);
        CREATE INDEX idx_upm_patient ON user_patient_managed(patient_id);

        COMMENT ON TABLE user_patient_managed IS '用户管理的患者列表（本人+家人）';
    END IF;
END$$;

-- ============================================================
-- 4. users 表外键关联 patient 表
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_name = 'users' AND constraint_name = 'fk_users_patient') THEN
        ALTER TABLE users ADD CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE SET NULL;
    END IF;
END$$;

-- ============================================================
-- 5. 插入演示数据（确保不覆盖已有数据）
-- ============================================================

-- 患者档案数据
INSERT INTO patient (id, real_name, id_card, gender, birthdate, phone, relation, is_primary, allergy_history)
SELECT 1, '患者小明', '210102199001011234', '男', '1990-01-01', '13800138000', '本人', 1, '青霉素过敏'
WHERE NOT EXISTS (SELECT 1 FROM patient WHERE id_card = '210102199001011234');

INSERT INTO patient (id, real_name, id_card, gender, birthdate, phone, relation, is_primary, allergy_history)
SELECT 2, '张小华', '210102196503151234', '女', '1965-03-15', '13900139000', '母亲', 0, '无'
WHERE NOT EXISTS (SELECT 1 FROM patient WHERE id_card = '210102196503151234');

INSERT INTO patient (id, real_name, id_card, gender, birthdate, phone, relation, is_primary, allergy_history)
SELECT 3, '李建国', '210102196203201234', '男', '1962-03-20', '13900139001', '父亲', 0, '磺胺类过敏'
WHERE NOT EXISTS (SELECT 1 FROM patient WHERE id_card = '210102196203201234');

-- 用户-患者管理关联数据
INSERT INTO user_patient_managed (user_id, patient_id)
SELECT 6, 1
WHERE NOT EXISTS (SELECT 1 FROM user_patient_managed WHERE user_id = 6 AND patient_id = 1);

INSERT INTO user_patient_managed (user_id, patient_id)
SELECT 6, 2
WHERE NOT EXISTS (SELECT 1 FROM user_patient_managed WHERE user_id = 6 AND patient_id = 2);

INSERT INTO user_patient_managed (user_id, patient_id)
SELECT 6, 3
WHERE NOT EXISTS (SELECT 1 FROM user_patient_managed WHERE user_id = 6 AND patient_id = 3);

-- 更新 users 表关联 patient_id
UPDATE users SET patient_id = 1 WHERE username = 'patient001' AND patient_id IS NULL;

-- ============================================================
-- 迁移完成
-- ============================================================