package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * AI生成排班请求 DTO
 */
@Data
public class ScheduleGenerateDTO implements Serializable {

    private Long departmentId;
    private String month; // YYYY-MM
    private String generateType; // full: 完整生成, incremental: 增量生成
    private List<ScheduleItemDTO> schedules;
    private Integer aiVersion;

    /**
     * 排班项目 DTO
     */
    @Data
    public static class ScheduleItemDTO implements Serializable {
        private Long physicianId;
        private LocalDate workDate;
        private String timeSlot;
        private Integer totalQuota;
        private String aiSuggestion;
    }
}
