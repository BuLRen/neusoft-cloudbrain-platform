-- 027: register 表新增叫号系统字段
-- 设计文档：task_requirements/设计文档/04_叫号系统设计文档.md
--
-- 新增 4 个字段：
--   call_status    SMALLINT DEFAULT 0  呼叫状态：0未叫/1已叫/2已应答/3过号
--   called_time    TIMESTAMP NULL      最近一次被叫的时刻（5 分钟未应答自动过号用）
--   answered_time  TIMESTAMP NULL      患者应答（进诊室）时刻
--   call_round     SMALLINT DEFAULT 0  已叫次数；>=2 后过号为终态，禁止重叫
--
-- 旧逻辑兼容：历史挂号记录的新字段全部为默认值/NULL，不影响现有查询。

ALTER TABLE register
    ADD COLUMN IF NOT EXISTS call_status SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS called_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS answered_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS call_round SMALLINT NOT NULL DEFAULT 0;

-- call_status 取值约束：仅允许 0/1/2/3
ALTER TABLE register
    DROP CONSTRAINT IF EXISTS chk_register_call_status;
ALTER TABLE register
    ADD CONSTRAINT chk_register_call_status CHECK (call_status IN (0, 1, 2, 3));

-- call_round 取值约束：非负，上限 2（设计文档 §9.2 过号上限）
ALTER TABLE register
    DROP CONSTRAINT IF EXISTS chk_register_call_round;
ALTER TABLE register
    ADD CONSTRAINT chk_register_call_round CHECK (call_round BETWEEN 0 AND 2);

-- 状态一致性约束：已应答必须有应答时间
-- 防御性约束，避免业务代码漏写 answered_time
ALTER TABLE register
    DROP CONSTRAINT IF EXISTS chk_register_call_answered_consistency;
ALTER TABLE register
    ADD CONSTRAINT chk_register_call_answered_consistency
    CHECK (call_status <> 2 OR answered_time IS NOT NULL);

COMMENT ON COLUMN register.call_status   IS '呼叫状态: 0-未叫, 1-已叫待应答, 2-已应答(进诊室), 3-过号';
COMMENT ON COLUMN register.called_time   IS '最近一次被叫时刻；应答超时定时任务据此判断 5 分钟自动过号';
COMMENT ON COLUMN register.answered_time IS '患者应答（进诊室）时刻；call_status=2 时必填';
COMMENT ON COLUMN register.call_round    IS '已叫次数；达到 2 次后过号为终态，禁止重叫，转人工处理';
