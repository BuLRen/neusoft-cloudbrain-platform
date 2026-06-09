package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Check Request Entity - 检查申请表（对齐 init.sql check_request）
 */
@Data
public class CheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long medicalTechnologyId;
    private String checkInfo;
    private String checkPosition;
    private String checkRemark;
    private String checkState;
    private String checkResult;
    private LocalDateTime checkTime;
    private LocalDateTime creationTime;
    private Long checkEmployeeId;
    private Long inputcheckEmployeeId;

    // JOIN 展示字段
    private String caseNumber;
    private String patientName;
    private String techName;
}
