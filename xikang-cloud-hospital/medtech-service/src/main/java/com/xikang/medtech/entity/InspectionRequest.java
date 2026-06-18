package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Inspection Request Entity - 检验申请表（对齐 init.sql inspection_request）
 */
@Data
public class InspectionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long medicalTechnologyId;
    private String inspectionInfo;
    private String inspectionPosition;
    private String inspectionRemark;
    private String inspectionState;
    private String inspectionResult;
    private LocalDateTime inspectionTime;
    private LocalDateTime creationTime;
    private Long inspectionEmployeeId;
    private Long inputinspectionEmployeeId;

    // JOIN 展示字段
    private String caseNumber;
    private String patientName;
    private String techName;
    private String techCode;
}
