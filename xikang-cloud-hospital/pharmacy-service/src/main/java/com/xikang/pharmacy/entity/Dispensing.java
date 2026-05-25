package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispensing Entity
 */
@Data
public class Dispensing implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long prescriptionId;
    private Long patientId;
    private String dispensingNo;
    private BigDecimal amount;
    private Integer status;
    private String pharmacist;
    private LocalDateTime dispensingTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
