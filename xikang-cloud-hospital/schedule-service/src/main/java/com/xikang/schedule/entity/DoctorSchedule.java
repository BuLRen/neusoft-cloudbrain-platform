package com.xikang.schedule.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医生出诊明细表
 * doctor_schedule: 具体的某医生某天某时段出诊信息
 */
@Data
public class DoctorSchedule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;             // 排班计划ID
    private Long physicianId;        // 医生ID
    private Long departmentId;       // 科室ID
    private LocalDate workDate;      // 出诊日期
    private String timeSlot;        // 时段：上午/下午/晚上
    private Long registLevelId;     // 挂号级别ID
    private Integer totalQuota;     // 总号源
    private Integer usedQuota;      // 已用号源
    private Integer availableQuota; // 剩余号源
    private BigDecimal price;       // 挂号费
    private String status;          // 状态：正常/停诊/满诊/替班
    private String aiSuggestion;    // AI建议
    private Boolean modified;      // 是否被修改
    private String modifyRemark;   // 修改备注
    private LocalDateTime createdTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
    private Integer delmark;        // 删除标记：0=有效/1=删除

    // 扩展字段（非数据库字段）
    private String physicianName;   // 医生姓名
    private String physicianTitle;  // 医生职称
    private String departmentName;  // 科室名称
    private String registLevelName; // 挂号级别名称
}