package com.xikang.schedule.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排班调整申请表
 * schedule_adjust_request: 记录排班调整申请，需管理员确认
 */
@Data
public class ScheduleAdjustRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long scheduleId;         // 排班ID
    private String adjustType;     // 调整类型：leave_ai/admin_urgent/system
    private Long oldPhysicianId;   // 原医生ID
    private Long newPhysicianId;   // 新医生ID
    private String oldStatus;      // 原状态
    private String newStatus;      // 新状态
    private Integer oldQuota;      // 原号源
    private Integer newQuota;      // 新号源
    private String reason;         // 调整原因
    private String aiSuggestion;   // AI建议
    private Integer affectPatients; // 影响患者数
    private Long triggeredBy;       // 触发人
    private String status;         // 状态：待确认/已确认/已驳回
    private Long confirmedBy;      // 确认人
    private LocalDateTime confirmTime; // 确认时间
    private String confirmRemark;  // 确认备注
    private LocalDateTime createTime; // 创建时间
    private Integer delmark; // 删除标记：0=有效/1=删除

    // 扩展字段（非数据库字段）
    private String originalPhysicianName; // 原医生姓名
    private String substitutePhysicianName; // 替班医生姓名
    private String workDate;        // 出诊日期
    private String timeSlot;       // 时段
    private String triggeredByName; // 触发人姓名
    private String confirmedByName; // 确认人姓名
}