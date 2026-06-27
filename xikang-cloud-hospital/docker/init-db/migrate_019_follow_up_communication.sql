-- 随访医患沟通：会话、消息、AI 病例总结
-- 可重复执行

CREATE TABLE IF NOT EXISTS follow_up_communication_session (
    id                      SERIAL          PRIMARY KEY,
    register_id             INTEGER         NOT NULL REFERENCES register(id),
    department_id           INTEGER         NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'active',
    ai_escalation_enabled   SMALLINT        NOT NULL DEFAULT 1,
    doctor_last_active_at   TIMESTAMP       DEFAULT NULL,
    creation_time           TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_followup_comm_session_register UNIQUE (register_id),
    CONSTRAINT chk_followup_comm_session_status CHECK (status IN ('active', 'closed'))
);

CREATE INDEX IF NOT EXISTS idx_followup_comm_session_dept
    ON follow_up_communication_session(department_id);

COMMENT ON TABLE follow_up_communication_session IS '随访医患沟通会话';

CREATE TABLE IF NOT EXISTS follow_up_communication_message (
    id                  SERIAL          PRIMARY KEY,
    session_id          INTEGER         NOT NULL REFERENCES follow_up_communication_session(id) ON DELETE CASCADE,
    sender_type         VARCHAR(16)     NOT NULL,
    message_type        VARCHAR(16)     NOT NULL DEFAULT 'text',
    content             TEXT            NOT NULL,
    summary_id          INTEGER         DEFAULT NULL,
    workflow_run_id     VARCHAR(128)    DEFAULT NULL,
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_followup_comm_msg_sender CHECK (sender_type IN ('doctor', 'patient', 'ai', 'system')),
    CONSTRAINT chk_followup_comm_msg_type CHECK (message_type IN ('text', 'case_summary', 'notice'))
);

CREATE INDEX IF NOT EXISTS idx_followup_comm_msg_session
    ON follow_up_communication_message(session_id, creation_time);

COMMENT ON TABLE follow_up_communication_message IS '随访医患沟通消息';

CREATE TABLE IF NOT EXISTS follow_up_case_summary (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL REFERENCES register(id),
    session_id          INTEGER         NOT NULL REFERENCES follow_up_communication_session(id) ON DELETE CASCADE,
    ai_draft_content    TEXT,
    ai_medical_advice   TEXT,
    ai_risk_alerts      TEXT,
    doctor_content      TEXT,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft',
    shared_to_patient   SMALLINT        NOT NULL DEFAULT 0,
    workflow_run_id     VARCHAR(128)    DEFAULT NULL,
    model_id            VARCHAR(64)     DEFAULT NULL,
    approved_by         INTEGER         DEFAULT NULL,
    approved_at         TIMESTAMP       DEFAULT NULL,
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_followup_case_summary_status CHECK (status IN ('draft', 'approved', 'shared', 'revoked'))
);

CREATE INDEX IF NOT EXISTS idx_followup_case_summary_register
    ON follow_up_case_summary(register_id, creation_time DESC);

COMMENT ON TABLE follow_up_case_summary IS 'AI 病例总结（医生审核后可发布给患者）';

-- 演示种子：1001-1003 各一会话 + 消息 + draft 总结
INSERT INTO follow_up_communication_session (register_id, department_id, status, ai_escalation_enabled)
SELECT r.id, fp.department_id, 'active', 1
FROM register r
INNER JOIN follow_up_patient_profile fp ON fp.register_id = r.id
WHERE r.id IN (1001, 1002, 1003)
ON CONFLICT (register_id) DO NOTHING;

DELETE FROM follow_up_communication_message
WHERE session_id IN (
    SELECT id FROM follow_up_communication_session WHERE register_id IN (1001, 1002, 1003)
);

DELETE FROM follow_up_case_summary
WHERE register_id IN (1001, 1002, 1003);

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'system', 'notice', E'随访沟通会话已建立，医生将在此与您跟进康复情况。'
FROM follow_up_communication_session s WHERE s.register_id = 1001;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'doctor', 'text', E'张三您好，本周血氧指标有改善，请继续按医嘱用药。'
FROM follow_up_communication_session s WHERE s.register_id = 1001;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'patient', 'text', E'医生好，偶尔还有轻微咳嗽，是否正常？'
FROM follow_up_communication_session s WHERE s.register_id = 1001;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'system', 'notice', E'随访沟通会话已建立。'
FROM follow_up_communication_session s WHERE s.register_id = 1002;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'doctor', 'text', E'李四，请按时记录每日症状变化。'
FROM follow_up_communication_session s WHERE s.register_id = 1002;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'system', 'notice', E'随访沟通会话已建立。'
FROM follow_up_communication_session s WHERE s.register_id = 1003;

INSERT INTO follow_up_communication_message (session_id, sender_type, message_type, content)
SELECT s.id, 'patient', 'text', E'王五：最近夜间仍有不适，是否需要调整用药？'
FROM follow_up_communication_session s WHERE s.register_id = 1003;

INSERT INTO follow_up_case_summary (
    register_id, session_id, ai_draft_content, ai_medical_advice, ai_risk_alerts,
    doctor_content, status, shared_to_patient, model_id
)
SELECT
    1001,
    s.id,
    E'## 病例摘要\n- 患者张三，术后随访中\n- 近30天血氧饱和度呈上升趋势\n- 症状缓解评估：部分缓解\n- 无严重药物不良反应报告',
    E'建议继续当前康复方案，保持每日指标监测；若咳嗽加重或血氧下降，请及时复诊。',
    E'["偶发咳嗽需关注","避免剧烈运动"]',
    NULL,
    'draft',
    0,
    'fallback-v1'
FROM follow_up_communication_session s WHERE s.register_id = 1001;
