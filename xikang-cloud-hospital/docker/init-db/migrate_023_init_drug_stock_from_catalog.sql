-- ============================================================
-- migrate_023_init_drug_stock_from_catalog.sql
-- 为 drug_info 全量目录初始化 drug_stock 批次库存
--
-- 前提：已执行 migrate_008_pharmacy_schema.sql
-- 默认：每药 100 盒，批号 INIT-{drug_code}，效期 +2 年
-- 幂等：已有批次的药品不会重复插入，可重复执行
-- ============================================================

-- 1) 为尚无批次的药品插入初始批次
INSERT INTO drug_stock (drug_id, batch_number, quantity, production_date, expiry_date, status, location)
SELECT
    d.id,
    'INIT-' || d.drug_code,
    100,
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '2 years',
    1,
    'DEFAULT'
FROM drug_info d
WHERE NOT EXISTS (
    SELECT 1 FROM drug_stock s WHERE s.drug_id = d.id
);

-- 2) 回写 drug_info 汇总库存与默认值
UPDATE drug_info d
SET stock_quantity = COALESCE((
    SELECT SUM(s.quantity) FROM drug_stock s
    WHERE s.drug_id = d.id AND s.status = 1
), 0),
    low_stock_threshold = COALESCE(low_stock_threshold, 20),
    status = COALESCE(status, 1);

-- 3) 同步 drug_stock 序列
SELECT setval(pg_get_serial_sequence('drug_stock', 'id'),
              COALESCE((SELECT MAX(id) FROM drug_stock), 1), true);
