-- Agent 待确认写操作（一次性确认令牌）
CREATE TABLE IF NOT EXISTS agent_pending_confirmation (
    id                BIGSERIAL PRIMARY KEY,
    token             VARCHAR(64)  NOT NULL UNIQUE,
    register_id       BIGINT       NOT NULL,
    doctor_id         BIGINT       NOT NULL,
    session_id        BIGINT,
    action_type       VARCHAR(64)  NOT NULL,
    payload_json      TEXT         NOT NULL,
    expires_at        TIMESTAMP    NOT NULL,
    consumed_at       TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_pending_token
    ON agent_pending_confirmation (token);

CREATE INDEX IF NOT EXISTS idx_agent_pending_register
    ON agent_pending_confirmation (register_id, created_at DESC);
