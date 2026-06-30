-- 医师 AI Copilot 多会话支持
CREATE TABLE IF NOT EXISTS physician_ai_chat_session (
    id          BIGSERIAL PRIMARY KEY,
    register_id BIGINT       NOT NULL,
    doctor_id   BIGINT,
    title       VARCHAR(128) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_physician_ai_chat_session_register
    ON physician_ai_chat_session (register_id, updated_at DESC);

ALTER TABLE physician_ai_chat_history
    ADD COLUMN IF NOT EXISTS session_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_physician_ai_chat_session
    ON physician_ai_chat_history (session_id, created_at DESC);
