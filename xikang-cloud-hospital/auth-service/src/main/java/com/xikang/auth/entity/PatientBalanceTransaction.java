package com.xikang.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Patient Balance Transaction Entity - 患者余额流水表
 */
@Data
public class PatientBalanceTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String transactionNo;
    private Integer patientId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String businessType;
    private Long businessId;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime transactionTime;
    private LocalDateTime createTime;
}
