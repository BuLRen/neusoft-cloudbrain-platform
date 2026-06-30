-- 医师 AI Copilot 对话历史（按就诊号隔离）
CREATE TABLE IF NOT EXISTS physician_ai_chat_history (
    id              BIGSERIAL PRIMARY KEY,
    register_id     BIGINT       NOT NULL,
    doctor_id       BIGINT,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    tool_calls_json TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_physician_ai_chat_register
    ON physician_ai_chat_history (register_id, created_at DESC);
