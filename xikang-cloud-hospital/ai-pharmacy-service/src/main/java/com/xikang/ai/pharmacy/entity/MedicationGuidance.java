package com.xikang.ai.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Medication Guidance Entity
 */
@Data
public class MedicationGuidance implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long prescriptionId;
    private String drugName;
    private String dosage;
    private String timing;
    private String sideEffects;
    private String precautions;
    private String interactions;
    private Integer status;
    private LocalDateTime createTime;
}
