-- ============================================================
-- 迁移 004: 患者账户余额与用户-患者关系字段
-- 说明: 支持患者端账户余额、充值、余额支付，以及修复 user_patient_managed.relation 与代码不一致问题
-- ============================================================

ALTER TABLE patient
    ADD COLUMN IF NOT EXISTS account_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00;

ALTER TABLE user_patient_managed
    ADD COLUMN IF NOT EXISTS relation VARCHAR(16);

WITH ranked AS (
    SELECT user_id, patient_id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY create_time ASC, patient_id ASC) AS rn
    FROM user_patient_managed
)
UPDATE user_patient_managed upm
SET relation = CASE WHEN ranked.rn = 1 THEN '本人' ELSE '其他' END
FROM ranked
WHERE upm.user_id = ranked.user_id
  AND upm.patient_id = ranked.patient_id
  AND (upm.relation IS NULL OR upm.relation = '');

UPDATE user_patient_managed
SET relation = '其他'
WHERE relation IS NULL OR relation = '';

CREATE INDEX IF NOT EXISTS idx_patient_account_balance ON patient(account_balance);

COMMENT ON COLUMN patient.account_balance IS '患者账户余额';
COMMENT ON COLUMN user_patient_managed.relation IS '账号与该患者的关系';
