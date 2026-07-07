-- ============================================================
-- migrate_034_notification.sql
-- 消息通知系统：统一消息表（承载医生变更、请假审批、缴费提醒等多类消息）
-- ============================================================

CREATE TABLE IF NOT EXISTS notification (
    id              BIGSERIAL    PRIMARY KEY,
    receiver_id     BIGINT       NOT NULL,                    -- 接收者 user_id（patient.id / employee.id）
    receiver_role   VARCHAR(20)  NOT NULL,                    -- patient / physician / admin
    type            VARCHAR(50)  NOT NULL,                    -- doctor_change / leave_approved / leave_rejected / adjust_pending / adjust_confirmed
    title           VARCHAR(100) NOT NULL,                    -- 消息标题
    content         TEXT         NOT NULL,                    -- 消息正文
    biz_type        VARCHAR(50),                              -- 关联业务类型（schedule_adjust_request / leave_request）
    biz_id          BIGINT,                                   -- 关联业务 ID
    is_read         SMALLINT     NOT NULL DEFAULT 0,          -- 0=未读, 1=已读
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,          -- 0=正常, 1=已软删
    created_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    CONSTRAINT chk_notif_role  CHECK (receiver_role IN ('patient','physician','admin')),
    CONSTRAINT chk_notif_read  CHECK (is_read    IN (0,1)),
    CONSTRAINT chk_notif_del   CHECK (is_deleted IN (0,1))
);

CREATE INDEX IF NOT EXISTS idx_notif_receiver ON notification(receiver_id, receiver_role, is_deleted, is_read);
CREATE INDEX IF NOT EXISTS idx_notif_biz      ON notification(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_notif_created  ON notification(created_time DESC);

COMMENT ON TABLE  notification              IS '统一消息通知表';
COMMENT ON COLUMN notification.receiver_id   IS '接收者 ID（患者/医生/管理员各自主键）';
COMMENT ON COLUMN notification.receiver_role IS '接收者角色';
COMMENT ON COLUMN notification.type          IS '消息类型：doctor_change/leave_approved/leave_rejected/adjust_pending/adjust_confirmed';
COMMENT ON COLUMN notification.biz_type      IS '业务关联类型：schedule_adjust_request/leave_request';
COMMENT ON COLUMN notification.biz_id        IS '业务关联 ID';
COMMENT ON COLUMN notification.is_read       IS '0=未读, 1=已读';
COMMENT ON COLUMN notification.is_deleted    IS '0=正常, 1=用户已删除';
