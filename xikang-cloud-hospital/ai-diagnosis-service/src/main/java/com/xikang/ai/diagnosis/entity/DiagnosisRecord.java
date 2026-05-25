package com.xikang.ai.diagnosis.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Diagnosis Record Entity
 */
@Data
public class DiagnosisRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registrationId;
    private String symptoms;
    private String primaryDiagnosis;
    private String differentialDiagnoses;
    private BigDecimal confidence;
    private String icdCodes;
    private String recommendedTests;
    private LocalDateTime createTime;
}
