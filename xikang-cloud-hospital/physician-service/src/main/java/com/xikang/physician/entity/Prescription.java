package com.xikang.physician.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prescription Entity
 */
@Data
public class Prescription implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registrationId;
    private Long patientId;
    private String prescriptionNo;
    private Integer type;
    private BigDecimal totalAmount;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
