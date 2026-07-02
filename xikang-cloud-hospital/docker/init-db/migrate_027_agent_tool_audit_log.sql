-- Agent 工具调用审计日志
CREATE TABLE IF NOT EXISTS agent_tool_audit_log (
    id                BIGSERIAL PRIMARY KEY,
    register_id       BIGINT       NOT NULL,
    doctor_id         BIGINT,
    session_id        BIGINT,
    request_id        VARCHAR(64),
    tool_name         VARCHAR(128) NOT NULL,
    risk_level        VARCHAR(16)  NOT NULL DEFAULT 'read',
    request_payload   TEXT,
    response_payload  TEXT,
    before_snapshot   TEXT,
    after_snapshot    TEXT,
    confirm_source    VARCHAR(32),
    confirmation_token VARCHAR(64),
    success           BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message     TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_register
    ON agent_tool_audit_log (register_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_doctor
    ON agent_tool_audit_log (doctor_id, created_at DESC);
