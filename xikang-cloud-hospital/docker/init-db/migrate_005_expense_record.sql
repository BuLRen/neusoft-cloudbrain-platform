-- ============================================================
-- 迁移 005: 费用记录表
-- 说明: 支持挂号费生成待缴费项目、患者余额自动支付、窗口收费查询
-- ============================================================

CREATE TABLE IF NOT EXISTS expense_record (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         DEFAULT NULL,
    patient_id      BIGINT          DEFAULT NULL,
    patient_name    VARCHAR(64)     DEFAULT NULL,
    category_id     INTEGER         DEFAULT NULL,
    category_name   VARCHAR(64)     DEFAULT NULL,
    item_id         INTEGER         DEFAULT NULL,
    item_name       VARCHAR(128)    NOT NULL,
    item_code       VARCHAR(64)     DEFAULT NULL,
    quantity        INTEGER         NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    total_amount    DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    status          SMALLINT        NOT NULL DEFAULT 0,
    pay_time        TIMESTAMP       DEFAULT NULL,
    refund_time     TIMESTAMP       DEFAULT NULL,
    operator_id     BIGINT          DEFAULT NULL,
    operator_name   VARCHAR(64)     DEFAULT NULL,
    remark          VARCHAR(255)    DEFAULT NULL,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_expense_record_status CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT chk_expense_record_quantity CHECK (quantity > 0),
    CONSTRAINT chk_expense_record_amount CHECK (unit_price >= 0 AND total_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_expense_record_register_id ON expense_record(register_id);
CREATE INDEX IF NOT EXISTS idx_expense_record_patient_id ON expense_record(patient_id);
CREATE INDEX IF NOT EXISTS idx_expense_record_status ON expense_record(status);

COMMENT ON TABLE expense_record IS '费用记录表';
COMMENT ON COLUMN expense_record.status IS '费用状态: 0-待缴费, 1-已缴费, 2-已退款, 3-已作废';
