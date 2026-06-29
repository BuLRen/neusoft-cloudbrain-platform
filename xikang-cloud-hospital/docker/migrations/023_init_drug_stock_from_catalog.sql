-- ============================================================
-- 023_init_drug_stock_from_catalog.sql
-- 远程库执行用（与 init-db/migrate_023_init_drug_stock_from_catalog.sql 相同）
-- ============================================================

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

UPDATE drug_info d
SET stock_quantity = COALESCE((
    SELECT SUM(s.quantity) FROM drug_stock s
    WHERE s.drug_id = d.id AND s.status = 1
), 0),
    low_stock_threshold = COALESCE(low_stock_threshold, 20),
    status = COALESCE(status, 1);

SELECT setval(pg_get_serial_sequence('drug_stock', 'id'),
              COALESCE((SELECT MAX(id) FROM drug_stock), 1), true);
