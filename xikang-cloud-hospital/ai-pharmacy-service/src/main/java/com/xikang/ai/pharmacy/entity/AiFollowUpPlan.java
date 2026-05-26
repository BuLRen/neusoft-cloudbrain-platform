package com.xikang.ai.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI Follow-up Plan Entity - AI随访计划
 */
@Data
public class AiFollowUpPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;               // 患者ID
    private Long registerId;             // 挂号ID
    private Long prescriptionId;         // 处方ID
    private String planType;             // 计划类型：medication/recovery
    private LocalDate startDate;         // 开始日期
    private LocalDate endDate;           // 结束日期
    private String frequency;             // 频次：daily/weekly
    private String followUpItems;        // 随访项目JSON
    private String instructions;          // 用药指导
    private Integer status;              // 状态：0待执行/1进行中/2已完成/3已取消
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
