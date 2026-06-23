package com.xikang.schedule.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排班调整日志表
 * schedule_adjust_log: 记录所有排班调整操作
 */
@Data
public class ScheduleAdjustLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long scheduleId; // 排班ID
    private String fieldName;      // 调整字段名
    private String oldValue;       // 原值
    private String newValue;       // 新值
    private String adjustType;     // 调整类型
    private Long adjustBy;         // 调整人
    private LocalDateTime adjustTime; // 调整时间
    private String remark;         // 备注
    private Integer delmark;        // 删除标记：0=有效/1=删除

    // 扩展字段（非数据库字段）
    private String adjustByName;   // 调整人姓名
}