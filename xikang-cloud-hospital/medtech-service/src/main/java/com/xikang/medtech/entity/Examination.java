package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Examination Entity
 */
@Data
public class Examination implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registrationId;
    private Long patientId;
    private String examinationNo;
    private String examinationType;
    private String bodyPart;
    private Integer status;
    private String report;
    private String conclusion;
    private LocalDateTime examinationTime;
    private LocalDateTime reportTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
