-- 清除随访演示种子（register 1001-1005 及 B 类模拟指标）
-- 不修改 A 类核心业务表结构；仅删除 B/C 类演示数据
-- 可重复执行

-- 医患沟通演示会话
DELETE FROM follow_up_communication_message
WHERE session_id IN (
    SELECT id FROM follow_up_communication_session WHERE register_id BETWEEN 1001 AND 1005
);
DELETE FROM follow_up_case_summary WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM follow_up_communication_session WHERE register_id BETWEEN 1001 AND 1005;

-- 工作台演示日程/观察/在管配置
DELETE FROM follow_up_daily_observation WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM follow_up_day_schedule WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM follow_up_interview_schedule WHERE register_id BETWEEN 1001 AND 1005;
DELETE FROM follow_up_patient_profile WHERE register_id BETWEEN 1001 AND 1005;

-- B 类模拟指标（全库清除，避免疗效页回退到 mock 曲线）
DELETE FROM follow_up_health_metric;

-- C 类中从 B 类迁移的模拟/遗留指标
DELETE FROM patient_health_observation
WHERE source_type IN ('simulated', 'legacy_metric');

-- migrate_015 写入的演示 AI 随访记录（保留 A 类表中其他真实记录）
DELETE FROM ai_follow_up_record WHERE register_id BETWEEN 1001 AND 1005;

-- 演示在管池（C 类，若 migrate_020 已执行）
DELETE FROM follow_up_enrollment WHERE register_id BETWEEN 1001 AND 1005;
