package com.xikang.ai.triage.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Triage Record Entity
 */
@Data
public class TriageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;
    private String sessionId;
    private String symptoms;
    private String department;
    private String urgency;
    private String recommendation;
    private LocalDateTime createTime;
}
