-- ============================================================
-- migrate_007_master_data.sql
-- 说明：升级 drug_info 和 medical_technology 两张表，使其与
--       pharmacy-service / medtech-service 的 MyBatis Mapper 字段对齐。
--
-- 背景：这两张表的原始结构来自 init.sql（东软旧字段，如 drug_name/tech_code），
--       但 pharmacy/medtech 服务后来重写 entity 和 mapper xml 时，
--       采用了新的字段命名（如 drug_info.name、medical_technology.code），
--       导致 GET /api/pharmacy/drugs、/api/medtech/medical-technologies 报 500。
--       此迁移保留旧列不动（被外键引用），新增 mapper 期望的列，并迁移存量数据。
--
-- 幂等：所有 ADD COLUMN 都用 IF NOT EXISTS，可重复执行。
-- ============================================================

-- ==================== drug_info ====================
-- 新增 mapper 期望的列
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS name                 VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS generic_name         VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS brand_name           VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS specification        VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS dosage_form          VARCHAR(64);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS unit                 VARCHAR(16);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS approval_number      VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS price                DECIMAL(8,2)   NOT NULL DEFAULT 0.00;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS stock_quantity       INTEGER        NOT NULL DEFAULT 100;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS low_stock_threshold  INTEGER        NOT NULL DEFAULT 20;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS storage_conditions   VARCHAR(255);
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS instructions         TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS contraindications    TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS adverse_reactions    TEXT;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS status               SMALLINT       NOT NULL DEFAULT 1;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS create_time          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE drug_info ADD COLUMN IF NOT EXISTS update_time          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP;

-- 修正 check 约束（price 已经有 chk_drug_price 约束在 drug_price 上，新列 price 自带 NOT NULL DEFAULT）
ALTER TABLE drug_info ADD CONSTRAINT chk_drug_info_price_new CHECK (price >= 0);
ALTER TABLE drug_info ADD CONSTRAINT chk_drug_info_status CHECK (status IN (0, 1));

-- 迁移老数据到新列（仅当新列为空时）
UPDATE drug_info SET
    name                = COALESCE(name, drug_name),
    specification       = COALESCE(specification, drug_format),
    dosage_form         = COALESCE(dosage_form, drug_dosage),
    unit                = COALESCE(unit, drug_unit),
    approval_number     = COALESCE(approval_number, drug_code),
    price               = COALESCE(NULLIF(price, 0), drug_price, 0),
    instructions        = COALESCE(instructions, drug_type),
    create_time         = COALESCE(create_time, creation_date::timestamp),
    update_time         = COALESCE(update_time, creation_date::timestamp)
WHERE name IS NULL
   OR specification IS NULL
   OR approval_number IS NULL
   OR price IS NULL OR price = 0;

-- 建索引方便 mapper 排序与筛选
CREATE INDEX IF NOT EXISTS idx_drug_info_name      ON drug_info(name);
CREATE INDEX IF NOT EXISTS idx_drug_info_status    ON drug_info(status);
CREATE INDEX IF NOT EXISTS idx_drug_info_low_stock ON drug_info(status, stock_quantity);


-- ==================== medical_technology ====================
-- 新增 mapper 期望的列
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS name              VARCHAR(255);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS code              VARCHAR(64);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS type              VARCHAR(64);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS department_id     INTEGER;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS department_name   VARCHAR(64);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS price             DECIMAL(8,2)  NOT NULL DEFAULT 0.00;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS specimen_type     VARCHAR(64);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS container         VARCHAR(64);
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS instructions      TEXT;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS preparation       TEXT;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS turnaround_time   INTEGER;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS status            SMALLINT      NOT NULL DEFAULT 1;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS description       TEXT;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS create_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE medical_technology ADD COLUMN IF NOT EXISTS update_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE medical_technology ADD CONSTRAINT chk_medtech_price_new CHECK (price >= 0);
ALTER TABLE medical_technology ADD CONSTRAINT chk_medtech_status     CHECK (status IN (0, 1));

-- 迁移老数据（注意真实表列名是 deptment_id 拼写缺 e）
UPDATE medical_technology SET
    name              = COALESCE(name, tech_name),
    code              = COALESCE(code, tech_code),
    type              = COALESCE(type, tech_type),
    department_id     = COALESCE(department_id, deptment_id),
    price             = COALESCE(NULLIF(price, 0), tech_price, 0),
    instructions      = COALESCE(instructions, tech_format),
    description       = COALESCE(description, price_type),
    create_time       = COALESCE(create_time, CURRENT_TIMESTAMP),
    update_time       = COALESCE(update_time, CURRENT_TIMESTAMP)
WHERE name IS NULL
   OR code IS NULL
   OR price IS NULL OR price = 0;

-- department_name 从 department 表回填
UPDATE medical_technology mt
SET department_name = d.dept_name
FROM department d
WHERE mt.department_id = d.id
  AND (mt.department_name IS NULL OR mt.department_name = '');

CREATE INDEX IF NOT EXISTS idx_medtech_name      ON medical_technology(name);
CREATE INDEX IF NOT EXISTS idx_medtech_status    ON medical_technology(status);
CREATE INDEX IF NOT EXISTS idx_medtech_type_new  ON medical_technology(type);
CREATE INDEX IF NOT EXISTS idx_medtech_dept_new  ON medical_technology(department_id);

-- ============================================================
-- 迁移完成
-- ============================================================
