package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Scheduling Entity - 医生排班表
 */
@Data
public class Scheduling implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long physicianId;     // 医生ID
    private String physicianName;  // 医生姓名
    private Long departmentId;     // 科室ID
    private String departmentName; // 科室名称
    private LocalDate workDate;    // 工作日期
    private String timeSlot;       // 时间段：上午/下午/全天
    private Integer totalQuota;    // 总号源
    private Integer usedQuota;    // 已用号源
    private Integer status;        // 状态：0停诊/1可挂号
    private String remark;         // 备注
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
