package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 请假申请 DTO
 */
@Data
public class LeaveApplicationDTO implements Serializable {

    private Long physicianId;
    private LocalDate leaveDate;
    private String timeSlot;
    private String leaveType;
    private String reason;
    private String rawText; // 原始文本（供AI解析）
}