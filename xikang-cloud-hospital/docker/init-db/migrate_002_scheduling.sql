-- ============================================================
-- 增量迁移脚本：添加智能排班系统相关表
-- 运行方式：psql -U postgres -d xikang_hospital -f migrate_002_scheduling.sql
-- ============================================================

-- ============================================================
-- 1. 创建排班计划表 (schedule_plan)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'schedule_plan') THEN
        CREATE TABLE schedule_plan (
            id              SERIAL          PRIMARY KEY,
            plan_name       VARCHAR(64)     NOT NULL,
            department_id   INTEGER         NOT NULL,
            plan_month      VARCHAR(7)      NOT NULL,
            status          VARCHAR(16)     NOT NULL DEFAULT '草稿',
            ai_generated    BOOLEAN         NOT NULL DEFAULT FALSE,
            ai_version      INTEGER         DEFAULT NULL,
            total_schedules INTEGER         DEFAULT 0,
            total_quota     INTEGER         DEFAULT 0,
            created_by      INTEGER         DEFAULT NULL,
            created_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            published_time  TIMESTAMP       DEFAULT NULL,
            published_by    INTEGER         DEFAULT NULL,
            delmark         SMALLINT NOT NULL DEFAULT 0,

            CONSTRAINT fk_sp_department FOREIGN KEY (department_id) REFERENCES department(id),
            CONSTRAINT chk_sp_status CHECK (status IN ('草稿', '待审核', '已发布'))
        );

        CREATE INDEX idx_sp_dept_month ON schedule_plan(department_id, plan_month);
        CREATE INDEX idx_sp_status ON schedule_plan(status);

        COMMENT ON TABLE schedule_plan IS '排班计划表，按月/按科室管理';
        COMMENT ON COLUMN schedule_plan.plan_month IS '计划月份，格式：YYYY-MM';
        COMMENT ON COLUMN schedule_plan.ai_generated IS '是否由AI生成';
    END IF;
END$$;

-- ============================================================
-- 2. 创建医生出诊明细表 (doctor_schedule)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'doctor_schedule') THEN
        CREATE TABLE doctor_schedule (
            id              SERIAL          PRIMARY KEY,
            plan_id         INTEGER         NOT NULL,
            physician_id    INTEGER         NOT NULL,
            department_id   INTEGER         NOT NULL,
            work_date       DATE            NOT NULL,
            time_slot       VARCHAR(6)      NOT NULL,
            regist_level_id INTEGER         NOT NULL,
            total_quota     INTEGER         NOT NULL,
            used_quota      INTEGER         NOT NULL DEFAULT 0,
            available_quota INTEGER         NOT NULL,
            price           DECIMAL(8,2)    NOT NULL,
            status          VARCHAR(16)     NOT NULL DEFAULT '正常',
            ai_suggestion   VARCHAR(255)    DEFAULT NULL,
            modified        BOOLEAN         NOT NULL DEFAULT FALSE,
            modify_remark   VARCHAR(255)    DEFAULT NULL,
            created_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            delmark         SMALLINT        NOT NULL DEFAULT 0,

            CONSTRAINT fk_ds_plan FOREIGN KEY (plan_id) REFERENCES schedule_plan(id),
            CONSTRAINT fk_ds_physician FOREIGN KEY (physician_id) REFERENCES employee(id),
            CONSTRAINT fk_ds_department FOREIGN KEY (department_id) REFERENCES department(id),
            CONSTRAINT fk_ds_regist_level FOREIGN KEY (regist_level_id) REFERENCES regist_level(id),
            CONSTRAINT chk_ds_status CHECK (status IN ('正常', '停诊', '满诊', '替班')),
            CONSTRAINT chk_ds_time_slot CHECK (time_slot IN ('上午', '下午', '晚上'))
        );

        CREATE INDEX idx_ds_plan ON doctor_schedule(plan_id);
        CREATE INDEX idx_ds_date ON doctor_schedule(work_date);
        CREATE INDEX idx_ds_physician ON doctor_schedule(physician_id);
        CREATE INDEX idx_ds_department ON doctor_schedule(department_id);
        CREATE UNIQUE INDEX idx_ds_unique ON doctor_schedule(work_date, physician_id, time_slot) WHERE delmark = 0;

        COMMENT ON TABLE doctor_schedule IS '医生出诊明细表';
        COMMENT ON COLUMN doctor_schedule.time_slot IS '时段：上午/下午/晚上';
        COMMENT ON COLUMN doctor_schedule.physician_id IS '出诊医生ID';
    END IF;
END$$;

-- ============================================================
-- 3. 创建排班调整申请表 (schedule_adjust_request)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'schedule_adjust_request') THEN
        CREATE TABLE schedule_adjust_request (
            id                  SERIAL          PRIMARY KEY,
            schedule_id INTEGER         NOT NULL,
            adjust_type         VARCHAR(16)     NOT NULL,
            old_physician_id    INTEGER         DEFAULT NULL,
            new_physician_id    INTEGER         DEFAULT NULL,
            old_status          VARCHAR(16)     DEFAULT NULL,
            new_status          VARCHAR(16)     DEFAULT NULL,
            old_quota           INTEGER         DEFAULT NULL,
            new_quota           INTEGER         DEFAULT NULL,
            reason TEXT            DEFAULT NULL,
            ai_suggestion       TEXT            DEFAULT NULL,
            affect_patients     INTEGER         DEFAULT 0,
            triggered_by        INTEGER         NOT NULL,
            status VARCHAR(16)     NOT NULL DEFAULT '待确认',
            confirmed_by        INTEGER         DEFAULT NULL,
            confirm_time TIMESTAMP       DEFAULT NULL,
            confirm_remark      VARCHAR(255)    DEFAULT NULL,
            create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            delmark             SMALLINT        NOT NULL DEFAULT 0,

            CONSTRAINT fk_sar_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id),
            CONSTRAINT chk_sar_type CHECK (adjust_type IN ('leave_ai', 'admin_urgent', 'system')),
            CONSTRAINT chk_sar_status CHECK (status IN ('待确认', '已确认', '已驳回'))
        );

        CREATE INDEX idx_sar_status ON schedule_adjust_request(status);
        CREATE INDEX idx_sar_schedule ON schedule_adjust_request(schedule_id);
        CREATE INDEX idx_sar_triggered_by ON schedule_adjust_request(triggered_by);

        COMMENT ON TABLE schedule_adjust_request IS '排班调整申请表';
        COMMENT ON COLUMN schedule_adjust_request.adjust_type IS '调整类型：leave_ai-请假AI调整/admin_urgent-管理员紧急调整/system-系统调整';
        COMMENT ON COLUMN schedule_adjust_request.affect_patients IS '预计影响患者数';
    END IF;
END$$;

-- ============================================================
-- 4. 创建医生请假申请表 (leave_request)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'leave_request') THEN
        CREATE TABLE leave_request (
            id              SERIAL          PRIMARY KEY,
            physician_id    INTEGER         NOT NULL,
            leave_date      DATE            NOT NULL,
            time_slot       VARCHAR(6)      DEFAULT NULL,
            leave_type      VARCHAR(32)     NOT NULL,
            reason          TEXT            DEFAULT NULL,
            ai_parsed_date  DATE            DEFAULT NULL,
            ai_parsed_slot  VARCHAR(6)      DEFAULT NULL,
            ai_confidence DECIMAL(3,2)    DEFAULT NULL,
            status          VARCHAR(16)     NOT NULL DEFAULT '待审批',
            approver_id     INTEGER         DEFAULT NULL,
            approval_time   TIMESTAMP       DEFAULT NULL,
            auto_processed  BOOLEAN         NOT NULL DEFAULT FALSE,
            create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            delmark         SMALLINT        NOT NULL DEFAULT 0,

            CONSTRAINT fk_lr_physician FOREIGN KEY (physician_id) REFERENCES employee(id),
            CONSTRAINT chk_lr_type CHECK (leave_type IN ('事假', '病假', '公假', '其他')),
            CONSTRAINT chk_lr_status CHECK (status IN ('待审批', '已批准', '已拒绝', '已处理')),
            CONSTRAINT chk_lr_slot CHECK (time_slot IS NULL OR time_slot IN ('上午', '下午', '全天'))
        );

        CREATE INDEX idx_lr_physician ON leave_request(physician_id);
        CREATE INDEX idx_lr_date ON leave_request(leave_date);
        CREATE INDEX idx_lr_status ON leave_request(status);

        COMMENT ON TABLE leave_request IS '医生请假申请表';
        COMMENT ON COLUMN leave_request.ai_parsed_date IS 'AI解析后的日期';
        COMMENT ON COLUMN leave_request.ai_confidence IS 'AI解析置信度';
        COMMENT ON COLUMN leave_request.auto_processed IS '是否已自动处理（生成调整方案）';
    END IF;
END$$;

-- ============================================================
-- 5. 创建排班调整日志表 (schedule_adjust_log)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'schedule_adjust_log') THEN
        CREATE TABLE schedule_adjust_log (
            id              SERIAL          PRIMARY KEY,
            schedule_id     INTEGER         NOT NULL,
            field_name      VARCHAR(32)     NOT NULL,
            old_value       VARCHAR(255)    DEFAULT NULL,
            new_value       VARCHAR(255)    DEFAULT NULL,
            adjust_type     VARCHAR(16)     NOT NULL,
            adjust_by       INTEGER         NOT NULL,
            adjust_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
            remark          VARCHAR(255)    DEFAULT NULL,
            delmark         SMALLINT        NOT NULL DEFAULT 0,

            CONSTRAINT fk_sal_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id)
        );

        CREATE INDEX idx_sal_schedule ON schedule_adjust_log(schedule_id);
        CREATE INDEX idx_sal_adjust_by ON schedule_adjust_log(adjust_by);

        COMMENT ON TABLE schedule_adjust_log IS '排班调整日志表';
        COMMENT ON COLUMN schedule_adjust_log.field_name IS '调整字段名';
    END IF;
END$$;

-- ============================================================
-- 6. 插入演示数据（确保不覆盖已有数据）
-- ============================================================

-- 科室数据（假设已有）
DO $$
BEGIN
    --插入测试排班计划
    INSERT INTO schedule_plan (id, plan_name, department_id, plan_month, status, ai_generated, total_schedules, total_quota)
    SELECT 1, '消化内科2026年6月排班', 4, '2026-06', '草稿', FALSE, 0, 0
    WHERE NOT EXISTS (SELECT 1 FROM schedule_plan WHERE id = 1);

    -- 插入测试医生出诊明细（如果有employee数据的话）
    -- 注意：需要先有employee数据才能插入，这里作为占位
END$$;

-- ============================================================
-- 迁移完成
-- ============================================================