-- migrate_013_medication_fee_unique.sql
-- 为 expense_record 增加 (register_id, item_code) 的部分唯一索引，
-- 仅约束 MEDICATION_FEE 行（每个挂号最多一行药品费），用于幂等出账兜底。
-- REGISTRATION_FEE 已有应用层去重逻辑（invalidateDuplicateRegistrationFees），不在本次约束范围。

-- 先清理潜在的历史脏数据：同一 (register_id, 'MEDICATION_FEE') 的多行只保留最新一行
DELETE FROM expense_record
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY register_id, item_code ORDER BY id DESC) AS rn
        FROM expense_record
        WHERE item_code = 'MEDICATION_FEE'
    ) t
    WHERE t.rn > 1
);

-- 部分唯一索引：仅约束 MEDICATION_FEE
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_medication_fee
    ON expense_record (register_id)
    WHERE item_code = 'MEDICATION_FEE';
