package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registration Entity - 挂号记录表
 */
@Data
public class Register implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;           // 患者ID
    private String patientName;       // 患者姓名
    private String patientPhone;      // 患者电话
    private String idCard;           // 身份证号
    private Long departmentId;        // 科室ID
    private String departmentName;    // 科室名称
    private Long physicianId;         // 医生ID
    private String physicianName;     // 医生姓名
    private Long schedulingId;        // 排班ID
    private LocalDate visitDate;      // 就诊日期
    private String visitTime;         // 就诊时间段
    private String complaint;         // 主诉
    private Integer status;           // 状态：0待缴费/1已缴费/2已接诊/3已完成/4已取消
    private Integer registerType;     // 挂号类型：0普通/1专家
    private Long registLevelId;       // 挂号级别ID
    private String registLevelName;   // 挂号级别名称
    private BigDecimal amount;        // 挂号费用
    private Integer payStatus;        // 缴费状态：0待支付/1已支付/2已退款
    private String payMethod;         // 支付方式
    private String aiTriageResult;   // AI导诊结果JSON
    private String aiPreVisit;       // AI预问诊摘要JSON
    private Long operatorId;         // 操作员ID
    private String operatorName;      // 操作员姓名
    private String remark;           // 备注
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
