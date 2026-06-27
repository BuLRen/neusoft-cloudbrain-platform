package com.xikang.ai.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI Follow-up Plan Entity - AI随访计划（与 init.sql / Mapper 列一致）
 */
@Data
public class AiFollowUpPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long prescriptionId;
    private Integer followUpDay;
    private LocalDate plannedDate;
    private String followUpType;
    private String contentTemplate;
    private String planStatus;
    private LocalDateTime creationTime;
    private String modelId;
}
