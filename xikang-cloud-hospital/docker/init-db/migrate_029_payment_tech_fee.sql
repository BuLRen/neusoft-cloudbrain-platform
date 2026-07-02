-- migrate_029: 检查/检验/处置费出账幂等（source_id + partial unique index）

ALTER TABLE expense_record
    ADD COLUMN IF NOT EXISTS source_id INTEGER;

COMMENT ON COLUMN expense_record.source_id IS '业务来源ID（check_request.id / inspection_request.id / disposal_request.id）';

CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_check_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'CHECK_FEE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_inspection_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'INSPECTION_FEE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_disposal_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'DISPOSAL_FEE';

CREATE INDEX IF NOT EXISTS idx_expense_record_patient_status
    ON expense_record (patient_id, status);
