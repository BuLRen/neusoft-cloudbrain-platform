package com.xikang.schedule.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班计划表
 * schedule_plan: 按月/按科室管理排班
 */
@Data
public class SchedulePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String planName;          // 计划名称
    private Long departmentId;       // 科室ID
    private String planMonth;        // 计划月份（YYYY-MM）
    private String status;           // 状态：草稿/待审核/已发布
    private Boolean aiGenerated;     // 是否由AI生成
    private Integer aiVersion;       // AI版本
    private Integer totalSchedules;  // 排班总数
    private Integer totalQuota;      // 总号源
    private Long createdBy;          // 创建人
    private LocalDateTime createdTime; // 创建时间
    private LocalDateTime publishedTime; // 发布时间
    private Long publishedBy;        // 发布人
    private Integer delmark;         // 删除标记：0=有效/1=删除

    // 扩展字段
    private String departmentName;    // 科室名称（非数据库字段）
}