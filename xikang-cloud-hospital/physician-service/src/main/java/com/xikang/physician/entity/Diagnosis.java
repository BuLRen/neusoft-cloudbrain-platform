package com.xikang.physician.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Diagnosis Entity
 */
@Data
public class Diagnosis implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registrationId;
    private String diagnosis;
    private String icdCode;
    private String symptoms;
    private Integer severity;
    private String treatment;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
