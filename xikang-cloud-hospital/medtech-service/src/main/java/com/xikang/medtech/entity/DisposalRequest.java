package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Disposal Request Entity - 处置申请表（对齐 init.sql disposal_request）
 */
@Data
public class DisposalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long medicalTechnologyId;
    private String disposalInfo;
    private String disposalPosition;
    private String disposalRemark;
    private String disposalState;
    private String disposalResult;
    private LocalDateTime disposalTime;
    private LocalDateTime creationTime;
    private Long disposalEmployeeId;
    private Long inputdisposalEmployeeId;

    // JOIN 展示字段
    private String caseNumber;
    private String patientName;
    private String techName;
    private String techCode;
}
