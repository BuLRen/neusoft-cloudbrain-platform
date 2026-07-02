package com.xikang.ctviewer.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CtImagingAuditLog {
    private Long id;
    private Long userId;
    private Long employeeId;
    private Long departmentId;
    private String action;
    private String volumeId;
    private String sourceVolumeId;
    private Long checkRequestId;
    private Long registerId;
    private Boolean success;
    private String denialReason;
    private String clientIp;
    private LocalDateTime createdAt;
}
