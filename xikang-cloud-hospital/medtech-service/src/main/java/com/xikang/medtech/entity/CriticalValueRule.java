package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CriticalValueRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String techCode;
    private String fieldKey;
    private String itemName;
    private String unit;
    private BigDecimal criticalLow;
    private BigDecimal criticalHigh;
    private String severity;
    private Boolean enabled;
    private LocalDateTime creationTime;
}
