package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Inspection Request Entity - 检验申请表
 */
@Data
public class InspectionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;           // 挂号ID
    private Long patientId;           // 患者ID
    private String patientName;       // 患者姓名
    private Long physicianId;         // 开单医生ID
    private String physicianName;     // 开单医生姓名
    private Long medicalTechnologyId; // 医技项目ID
    private String medicalTechnologyName; // 医技项目名称
    private String specimenType;     // 样本类型
    private LocalDateTime specimenTime; // 采样时间
    private Integer status;           // 状态：0待缴费/1待执行/2执行中/3已完成/4已取消
    private LocalDateTime resultTime; // 出结果时间
    private String result;            // 检验结果JSON
    private String aiAnalysis;        // AI分析结果JSON
    private String remarks;           // 备注
    private Long operatorId;         // 操作人ID
    private String operatorName;       // 操作人姓名
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
