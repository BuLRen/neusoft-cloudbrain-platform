package com.xikang.ai.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI Follow-up Record Entity - AI随访记录
 */
@Data
public class AiFollowUpRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;                 // 计划ID
    private Long patientId;             // 患者ID
    private String medicationCompliance; // 依从性：good/general/poor
    private String symptomFeedback;      // 症状反馈
    private String sideEffects;          // 不良反应
    private String recoveryStatus;       // 康复状态
    private String aiAssessment;         // AI评估JSON
    private LocalDate nextFollowUpDate; // 下次随访日期
    private String remark;               // 备注
    private LocalDateTime recordTime;    // 记录时间
    private LocalDateTime createTime;
}
