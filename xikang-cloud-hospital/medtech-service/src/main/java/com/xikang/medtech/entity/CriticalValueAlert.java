package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CriticalValueAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private String patientName;
    private String caseNumber;
    private String sourceType;
    private Long sourceId;
    private String techName;
    private String criticalItems;
    private String severity;
    private Long reporterId;
    private String reporterName;
    private Long doctorId;
    private String doctorName;
    private String status;
    private LocalDateTime reportedTime;
    private LocalDateTime acknowledgedTime;
    private LocalDateTime handledTime;
    private String handleNote;
    private LocalDateTime escalatedTime;
    private LocalDateTime ackDeadline;
    private LocalDateTime creationTime;
}
