-- ============================================================
-- 迁移 006: 患者余额流水表
-- 说明: 为充值/扣款/退款提供真实钱包流水与业务幂等保护
-- ============================================================

CREATE TABLE IF NOT EXISTS patient_balance_transaction (
    id                BIGSERIAL       PRIMARY KEY,
    transaction_no    VARCHAR(64)     NOT NULL,
    patient_id        INTEGER         NOT NULL,
    transaction_type  VARCHAR(16)     NOT NULL,
    amount            DECIMAL(10,2)   NOT NULL,
    balance_before    DECIMAL(10,2)   NOT NULL,
    balance_after     DECIMAL(10,2)   NOT NULL,
    business_type     VARCHAR(64)     DEFAULT NULL,
    business_id       BIGINT          DEFAULT NULL,
    operator_id       BIGINT          DEFAULT NULL,
    operator_name     VARCHAR(64)     DEFAULT NULL,
    remark            VARCHAR(255)    DEFAULT NULL,
    transaction_time  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_patient_balance_transaction_no UNIQUE (transaction_no),
    CONSTRAINT fk_patient_balance_transaction_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT chk_patient_balance_transaction_type CHECK (transaction_type IN ('RECHARGE', 'DEDUCT', 'REFUND')),
    CONSTRAINT chk_patient_balance_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_patient_balance_transaction_balance CHECK (balance_before >= 0 AND balance_after >= 0)
);

CREATE INDEX IF NOT EXISTS idx_patient_balance_transaction_patient_time
    ON patient_balance_transaction(patient_id, transaction_time DESC);

CREATE INDEX IF NOT EXISTS idx_patient_balance_transaction_business
    ON patient_balance_transaction(patient_id, transaction_type, business_type, business_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_balance_transaction_business_unique
    ON patient_balance_transaction(patient_id, transaction_type, business_type, business_id)
    WHERE business_type IS NOT NULL AND business_id IS NOT NULL;

COMMENT ON TABLE patient_balance_transaction IS '患者余额流水表';
COMMENT ON COLUMN patient_balance_transaction.transaction_type IS '交易类型: RECHARGE-充值, DEDUCT-扣款, REFUND-退款';
COMMENT ON COLUMN patient_balance_transaction.transaction_no IS '唯一交易流水号';
COMMENT ON COLUMN patient_balance_transaction.business_type IS '业务类型，如 REGISTRATION';
COMMENT ON COLUMN patient_balance_transaction.business_id IS '业务ID，如挂号单ID';
COMMENT ON COLUMN patient_balance_transaction.balance_before IS '变更前余额';
COMMENT ON COLUMN patient_balance_transaction.balance_after IS '变更后余额';
