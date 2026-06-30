-- Dify Agent 多轮会话 ID（与 physician_ai_chat_session 一一对应）
ALTER TABLE physician_ai_chat_session
    ADD COLUMN IF NOT EXISTS dify_conversation_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_physician_ai_chat_session_dify_conv
    ON physician_ai_chat_session (dify_conversation_id)
    WHERE dify_conversation_id IS NOT NULL;
