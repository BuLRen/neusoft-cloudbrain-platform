-- 运营监控告警 dismiss 状态持久化
CREATE TABLE IF NOT EXISTS monitoring_alert_dismissal (
    id              BIGSERIAL PRIMARY KEY,
    alert_key       VARCHAR(128) NOT NULL UNIQUE,
    status          VARCHAR(16)  NOT NULL DEFAULT 'resolved',
    operator_id     INTEGER,
    operator_name   VARCHAR(64),
    dismissed_at    TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_monitoring_dismissal_key ON monitoring_alert_dismissal(alert_key);
