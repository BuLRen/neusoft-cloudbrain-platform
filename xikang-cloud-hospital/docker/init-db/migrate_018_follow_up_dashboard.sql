-- 随访工作台：患者纳入池、日级日程、每日观察确认
-- 可重复执行

CREATE TABLE IF NOT EXISTS follow_up_patient_profile (
    register_id                 INTEGER         PRIMARY KEY REFERENCES register(id),
    department_id               INTEGER         NOT NULL,
    priority_level              VARCHAR(16)     NOT NULL DEFAULT 'normal',
    interview_interval_days     INTEGER         NOT NULL DEFAULT 7,
    observation_interval_days   INTEGER         NOT NULL DEFAULT 1,
    enrolled_at                 TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_followup_priority CHECK (priority_level IN ('normal', 'high', 'critical')),
    CONSTRAINT chk_followup_interview_interval CHECK (interview_interval_days > 0),
    CONSTRAINT chk_followup_observation_interval CHECK (observation_interval_days > 0)
);

CREATE INDEX IF NOT EXISTS idx_followup_patient_profile_dept
    ON follow_up_patient_profile(department_id);

COMMENT ON TABLE follow_up_patient_profile IS '随访患者纳入池与访谈/观察周期配置';

CREATE TABLE IF NOT EXISTS follow_up_day_schedule (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         REFERENCES register(id),
    department_id   INTEGER         NOT NULL,
    schedule_date   DATE            NOT NULL,
    item_type       VARCHAR(16)     NOT NULL DEFAULT 'interview',
    title           VARCHAR(128)    NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'planned',
    created_by      INTEGER         DEFAULT NULL,
    creation_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_followup_day_item_type CHECK (item_type IN ('interview', 'observation', 'custom')),
    CONSTRAINT chk_followup_day_status CHECK (status IN ('planned', 'completed', 'cancelled'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_followup_day_schedule_interview
    ON follow_up_day_schedule(register_id, schedule_date, item_type)
    WHERE register_id IS NOT NULL AND item_type = 'interview';

CREATE INDEX IF NOT EXISTS idx_followup_day_schedule_dept_date
    ON follow_up_day_schedule(department_id, schedule_date);

COMMENT ON TABLE follow_up_day_schedule IS '随访日级工作安排（访谈/观察/自定义）';

CREATE TABLE IF NOT EXISTS follow_up_daily_observation (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL REFERENCES register(id),
    observation_date    DATE            NOT NULL,
    observed_by         INTEGER         DEFAULT NULL,
    confirmed_at        TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    note                TEXT            DEFAULT NULL,
    CONSTRAINT uk_followup_daily_observation UNIQUE (register_id, observation_date)
);

CREATE INDEX IF NOT EXISTS idx_followup_daily_observation_date
    ON follow_up_daily_observation(observation_date);

COMMENT ON TABLE follow_up_daily_observation IS '随访每日疗效观察确认记录';

-- 演示患者纳入随访池（科室 1: 1001/1002/1004，科室 2: 1003/1005）
INSERT INTO follow_up_patient_profile (
    register_id, department_id, priority_level, interview_interval_days, observation_interval_days
) VALUES
    (1001, 1, 'high', 3, 1),
    (1002, 1, 'normal', 7, 1),
    (1003, 2, 'critical', 1, 1),
    (1004, 1, 'normal', 7, 1),
    (1005, 2, 'high', 3, 1)
ON CONFLICT (register_id) DO UPDATE SET
    department_id = EXCLUDED.department_id,
    priority_level = EXCLUDED.priority_level,
    interview_interval_days = EXCLUDED.interview_interval_days;

-- 同步 register 科室（与种子一致）
UPDATE register SET deptment_id = 1 WHERE id IN (1001, 1002, 1004);
UPDATE register SET deptment_id = 2 WHERE id IN (1003, 1005);
UPDATE register SET visit_state = 3 WHERE id BETWEEN 1001 AND 1005;

-- 本周访谈日程示例
DELETE FROM follow_up_day_schedule WHERE register_id BETWEEN 1001 AND 1005;

INSERT INTO follow_up_day_schedule (register_id, department_id, schedule_date, item_type, title, status, created_by) VALUES
    (1001, 1, CURRENT_DATE, 'interview', E'张三 · 电话访谈', 'planned', 1),
    (1003, 2, CURRENT_DATE, 'interview', E'王五 · 重点随访访谈', 'planned', 2),
    (1002, 1, CURRENT_DATE + 2, 'interview', E'李四 · 随访访谈', 'planned', 1),
    (1005, 2, CURRENT_DATE + 1, 'interview', E'孙七 · 随访访谈', 'planned', 2),
    (1001, 1, CURRENT_DATE - 3, 'interview', E'张三 · 上周访谈', 'completed', 1),
    (1003, 2, CURRENT_DATE - 1, 'interview', E'王五 · 昨日访谈', 'completed', 2);

-- 今日观察：1002、1004 已确认，其余待观察
DELETE FROM follow_up_daily_observation WHERE register_id BETWEEN 1001 AND 1005 AND observation_date = CURRENT_DATE;

INSERT INTO follow_up_daily_observation (register_id, observation_date, observed_by, note) VALUES
    (1002, CURRENT_DATE, 1, E'指标平稳'),
    (1004, CURRENT_DATE, 1, E'已完成远程观察');
