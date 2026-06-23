-- ============================================================
-- 迁移脚本：migrate_008_pharmacy_schema
-- 说明：为 pharmacy-service 补齐 schema，使其能真正跑起来
--
-- 背景：
--   pharmacy-service 假设的 drug_info 列名（name/specification/dosage_form/
--   category/unit/price/stock_quantity 等）与真实表（drug_name/drug_format/
--   drug_dosage/drug_type/drug_unit/drug_price）完全不一致；
--   drug_stock / pharmacy_transaction / dispensing 三张表从未建过；
--   prescription 表是"一药一行"（physician-service 写入），pharmacy 期望的
--   "按挂号聚合 + dispensation_status/pharmacist/dispensation_time" 等列不存在。
--
-- 策略：
--   1) drug_info：在保留旧列的前提下 ADD COLUMN 一批 pharmacy 需要的列，
--      并用旧列值做一次性数据回填；旧列不删，physician-service 不受影响。
--   2) 新建 drug_stock（批号库存）、pharmacy_transaction（流水）、dispensing
--      （发药单）三张表。
--   3) prescription 表结构不动，pharmacy 侧的 Mapper 改为按 register_id 聚合
--      读，发药/退药改为 UPDATE prescription.drug_state。
--
-- 幂等：所有语句用 IF NOT EXISTS / ON CONFLICT 保护，可重复执行。
-- ============================================================

-- ============================================================
-- 1. 扩展 drug_info（保留旧列，新增 pharmacy 列）
-- ============================================================

ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS name              VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS generic_name      VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS brand_name        VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS specification     VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS dosage_form       VARCHAR(64);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS category          VARCHAR(64);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS unit              VARCHAR(32);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS approval_number   VARCHAR(64);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS price             DECIMAL(8,2) DEFAULT 0.00;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS stock_quantity       INTEGER      DEFAULT 0;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS low_stock_threshold  INTEGER      DEFAULT 10;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS storage_conditions VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS instructions       TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS contraindications  TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS adverse_reactions  TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS status             SMALLINT     DEFAULT 1;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS update_time        Timestamp    DEFAULT CURRENT_TIMESTAMP;

-- 从旧列回填新列（仅当新列为空时执行，避免覆盖已手工维护的数据）
UPDATE drug_info SET name = drug_name           WHERE name IS NULL AND drug_name IS NOT NULL;
UPDATE drug_info SET specification = drug_format WHERE specification IS NULL AND drug_format IS NOT NULL;
UPDATE drug_info SET dosage_form = drug_dosage   WHERE dosage_form IS NULL AND drug_dosage IS NOT NULL;
UPDATE drug_info SET category = drug_type        WHERE category IS NULL AND drug_type IS NOT NULL;
UPDATE drug_info SET unit = drug_unit            WHERE unit IS NULL AND drug_unit IS NOT NULL;
UPDATE drug_info SET price = drug_price          WHERE price IS NULL OR price = 0 AND drug_price IS NOT NULL;

-- ============================================================
-- 1.1 扩展 prescription 表（不修改原有列，仅追加）
--     pharmacy-service 在发药/退药时要写 dispensation_time / pharmacist，
--     并按挂号维度补齐诊断 remarks。prescription 表原本只有 drug_state 文本，
--     CHECK 约束保留为 ('未发','已发','已退')，不新增状态值。
-- ============================================================

ALTER TABLE prescription ADD COLUMN IF NOT EXISTS dispensation_time TIMESTAMP;
ALTER TABLE prescription ADD COLUMN IF NOT EXISTS pharmacist        VARCHAR(64);
ALTER TABLE prescription ADD COLUMN IF NOT EXISTS diagnosis         VARCHAR(255);
ALTER TABLE prescription ADD COLUMN IF NOT EXISTS remarks           VARCHAR(255);

COMMENT ON COLUMN prescription.dispensation_time IS '发药 / 退药时间（由 pharmacy-service 写入）';
COMMENT ON COLUMN prescription.pharmacist IS '操作药师姓名（由 pharmacy-service 写入）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_drug_info_dosage_form ON drug_info(dosage_form);
CREATE INDEX IF NOT EXISTS idx_drug_info_category    ON drug_info(category);
CREATE INDEX IF NOT EXISTS idx_drug_info_status      ON drug_info(status);

COMMENT ON COLUMN drug_info.name IS '药品名称（统一字段，等价于 drug_name）';
COMMENT ON COLUMN drug_info.specification IS '规格（等价于 drug_format）';
COMMENT ON COLUMN drug_info.dosage_form IS '剂型：片剂/胶囊/注射液等（等价于 drug_dosage）';
COMMENT ON COLUMN drug_info.category IS '分类：西药/中成药/OTC 等（等价于 drug_type）';
COMMENT ON COLUMN drug_info.unit IS '单位（等价于 drug_unit）';
COMMENT ON COLUMN drug_info.price IS '单价（等价于 drug_price）';
COMMENT ON COLUMN drug_info.stock_quantity IS '当前总可用库存（由 drug_stock 聚合，但冗余维护便于查询）';

-- ============================================================
-- 2. 药品库存批次表 drug_stock
--    一条 = 一个批号；某药品总量 = SUM(quantity) WHERE status=1
-- ============================================================

CREATE TABLE IF NOT EXISTS drug_stock (
    id              BIGSERIAL       PRIMARY KEY,
    drug_id         BIGINT          NOT NULL,
    batch_number    VARCHAR(64),
    quantity        INTEGER         NOT NULL DEFAULT 0,
    production_date DATE,
    expiry_date     DATE,
    status          SMALLINT        NOT NULL DEFAULT 1,   -- 0 冻结 / 1 可用
    location        VARCHAR(64),
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_drug_stock_drug
        FOREIGN KEY (drug_id) REFERENCES drug_info(id)
);

CREATE INDEX IF NOT EXISTS idx_drug_stock_drug_id  ON drug_stock(drug_id);
CREATE INDEX IF NOT EXISTS idx_drug_stock_status   ON drug_stock(status);
CREATE INDEX IF NOT EXISTS idx_drug_stock_expiry   ON drug_stock(expiry_date);

COMMENT ON TABLE drug_stock IS '药品库存批次表（一个批号一行）';
COMMENT ON COLUMN drug_stock.status IS '0=冻结，1=可用';
COMMENT ON COLUMN drug_stock.expiry_date IS '失效日期，近效期判断依据';

-- 初始化：给现有的 3 个药品各建一个充裕的初始批次，避免一上来全是低库存
INSERT INTO drug_stock (drug_id, batch_number, quantity, production_date, expiry_date, status, location)
SELECT id, 'INIT-' || drug_code, 100, CURRENT_DATE,
       CURRENT_DATE + INTERVAL '1 year', 1, 'A-1'
FROM drug_info
WHERE NOT EXISTS (
    SELECT 1 FROM drug_stock ds WHERE ds.drug_id = drug_info.id
);

-- 用批次总量回填 drug_info.stock_quantity
UPDATE drug_info d
SET stock_quantity = COALESCE((
    SELECT SUM(quantity) FROM drug_stock s
    WHERE s.drug_id = d.id AND s.status = 1
), 0);

-- ============================================================
-- 3. 药房交易流水表 pharmacy_transaction
--    每次发药 / 退药 / 入库 / 盘点 / 报损 一条
-- ============================================================

CREATE TABLE IF NOT EXISTS pharmacy_transaction (
    id               BIGSERIAL      PRIMARY KEY,
    type             VARCHAR(16)    NOT NULL,   -- 发放/退回/入库/盘点/报损
    drug_id          BIGINT,
    drug_name        VARCHAR(255),
    prescription_id  BIGINT,
    register_id      BIGINT,
    quantity         INTEGER,                   -- 正数=入库/退回，负数=发放/报损
    unit_price       DECIMAL(8,2),
    total_amount     DECIMAL(10,2),
    operator_id      BIGINT,
    operator_name    VARCHAR(64),
    reason           VARCHAR(255),
    transaction_time TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    create_time      Timestamp      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pharmacy_tx_type         ON pharmacy_transaction(type);
CREATE INDEX IF NOT EXISTS idx_pharmacy_tx_drug_id      ON pharmacy_transaction(drug_id);
CREATE INDEX IF NOT EXISTS idx_pharmacy_tx_register_id  ON pharmacy_transaction(register_id);
CREATE INDEX IF NOT EXISTS idx_pharmacy_tx_time         ON pharmacy_transaction(transaction_time);

COMMENT ON TABLE pharmacy_transaction IS '药房出入库流水（审计用）';
COMMENT ON COLUMN pharmacy_transaction.type IS '发放/退回/入库/盘点/报损';

-- ============================================================
-- 4. 发药单表 dispensing（面向患者的用药指导单，P2-4.6）
--    每次发药按"挂号 × 处方"生成一条
-- ============================================================

CREATE TABLE IF NOT EXISTS dispensing (
    id               BIGSERIAL      PRIMARY KEY,
    prescription_id  BIGINT,
    register_id      BIGINT,
    patient_id       BIGINT,
    dispensing_no    VARCHAR(64)    UNIQUE,
    amount           DECIMAL(10,2),
    status           SMALLINT       DEFAULT 1,   -- 1=已发药 2=已退药
    pharmacist       VARCHAR(64),
    dispensing_time  TIMESTAMP,
    complete_time    TIMESTAMP,
    create_time      Timestamp      DEFAULT CURRENT_TIMESTAMP,
    update_time      Timestamp      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dispensing_register_id    ON dispensing(register_id);
CREATE INDEX IF NOT EXISTS idx_dispensing_prescription_id ON dispensing(prescription_id);
CREATE INDEX IF NOT EXISTS idx_dispensing_patient_id     ON dispensing(patient_id);
CREATE INDEX IF NOT EXISTS idx_dispensing_no             ON dispensing(dispensing_no);

COMMENT ON TABLE dispensing IS '发药单（用药指导单，与 pharmacy_transaction 审计流水区分）';
COMMENT ON COLUMN dispensing.dispensing_no IS 'DY-yyyyMMddHHmmss-{registerId}-{prescriptionId}';

-- ============================================================
-- 5. 序列同步（以防本脚本在已有 drug_info 数据的环境中首次执行时序列错位）
-- ============================================================
SELECT setval(pg_get_serial_sequence('drug_stock', 'id'),
              COALESCE((SELECT MAX(id) FROM drug_stock), 1), true);
SELECT setval(pg_get_serial_sequence('pharmacy_transaction', 'id'),
              COALESCE((SELECT MAX(id) FROM pharmacy_transaction), 1), true);
SELECT setval(pg_get_serial_sequence('dispensing', 'id'),
              COALESCE((SELECT MAX(id) FROM dispensing), 1), true);

-- ============================================================
-- 6. 演示种子：待发药处方
--    让药房账号登录后立刻能在"待发药列表"看到数据。
--    依赖 init.sql 中已存在的 register(1001/1002) 和 drug_info(1/2/3)。
--    ON CONFLICT 保护，可重复执行；用 drug_state='未发' + register_id 组合做唯一性判别。
-- ============================================================

INSERT INTO prescription (register_id, drug_id, drug_usage, drug_number, drug_state, creation_time)
SELECT 1001, 1, '口服，每次1片，一日3次', '2', '未发', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM prescription
    WHERE register_id = 1001 AND drug_id = 1 AND drug_state = '未发'
);

INSERT INTO prescription (register_id, drug_id, drug_usage, drug_number, drug_state, creation_time)
SELECT 1001, 3, '口服，每次1粒，一日2次', '1', '未发', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM prescription
    WHERE register_id = 1001 AND drug_id = 3 AND drug_state = '未发'
);

INSERT INTO prescription (register_id, drug_id, drug_usage, drug_number, drug_state, creation_time)
SELECT 1002, 2, '口服，每次1粒，一日2次', '1', '未发', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM prescription
    WHERE register_id = 1002 AND drug_id = 2 AND drug_state = '未发'
);

-- 1001 的诊断备注（演示用）
UPDATE prescription SET diagnosis = '上呼吸道感染'
WHERE register_id = 1001 AND diagnosis IS NULL;
UPDATE prescription SET diagnosis = '腹痛待查'
WHERE register_id = 1002 AND diagnosis IS NULL;
