package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 排班 DTO
 */
@Data
public class ScheduleDTO implements Serializable {

    private Long scheduleId;
    private Long physicianId;
    private String physicianName;
    private String physicianTitle;
    private Long departmentId;
    private String departmentName;
    private LocalDate workDate;
    private String timeSlot;
    private Integer totalQuota;
    private Integer usedQuota;
    private Integer availableQuota;
    private String price;
    private String status;
    private String aiSuggestion;
}