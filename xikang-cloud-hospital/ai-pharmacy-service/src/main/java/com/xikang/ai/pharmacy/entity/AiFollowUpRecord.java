package com.xikang.ai.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Follow-up Record Entity - AI随访记录（与 init.sql 列一致）
 */
@Data
public class AiFollowUpRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long followUpPlanId;
    private Long registerId;
    private Integer isOnTime;
    private String sideEffect;
    private Integer hasSideEffect;
    private String symptomRelief;
    private Integer needRevisit;
    private String patientFeedback;
    private String aiAssessment;
    private String aiAdvice;
    private LocalDateTime followUpTime;
    private String modelId;
}
