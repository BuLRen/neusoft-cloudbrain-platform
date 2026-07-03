-- migrate_032: CT 影像访问审计日志
CREATE TABLE IF NOT EXISTS ct_imaging_audit_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT,
    employee_id       BIGINT,
    department_id     BIGINT,
    action            VARCHAR(32)  NOT NULL,
    volume_id         VARCHAR(64),
    source_volume_id  VARCHAR(64),
    check_request_id  BIGINT,
    register_id       BIGINT,
    success           BOOLEAN      NOT NULL DEFAULT TRUE,
    denial_reason     VARCHAR(255),
    client_ip         VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ct_audit_volume
    ON ct_imaging_audit_log (volume_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ct_audit_user
    ON ct_imaging_audit_log (user_id, created_at DESC);
