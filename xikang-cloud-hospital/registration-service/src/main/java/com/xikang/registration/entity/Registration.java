package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Registration Entity
 */
@Data
public class Registration implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;
    private String patientName;
    private Long departmentId;
    private String departmentName;
    private Long physicianId;
    private String physicianName;
    private LocalDateTime appointmentTime;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
