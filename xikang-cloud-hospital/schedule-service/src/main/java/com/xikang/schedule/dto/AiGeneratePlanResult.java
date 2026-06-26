package com.xikang.schedule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xikang.schedule.entity.SchedulePlan;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratePlanResult implements Serializable {
    private Long planId;
    private Integer scheduleCount;
    private Integer aiVersion;
    private String generateType;
    private SchedulePlan plan;

    @JsonProperty("validated_schedules")
    private List<ValidatedScheduleDTO> validatedSchedules;

    /** 内部使用：编排阶段产出的待落库排班对象，避免再走一次序列化 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient List<com.xikang.schedule.entity.DoctorSchedule> schedulesForPersist;

    public List<com.xikang.schedule.entity.DoctorSchedule> getSchedulesForPersist() {
        return schedulesForPersist;
    }

    public void setSchedulesForPersist(List<com.xikang.schedule.entity.DoctorSchedule> schedulesForPersist) {
        this.schedulesForPersist = schedulesForPersist;
    }

    @com.fasterxml.jackson.annotation.JsonSetter("validated_schedules")
    public void setValidatedSchedulesFromString(com.fasterxml.jackson.databind.JsonNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
        applyValidatedSchedulesNode(node);
    }

    /**
     * v4.3：Dify Code 节点对 Array 输出有 30 元素限制，节点5 已改为输出
     * {@code validated_schedules_json}（String，JSON 序列化后的字符串）。
     * 后端通过这个别名 setter 接收，复用同一套解析逻辑。
     */
    @com.fasterxml.jackson.annotation.JsonSetter("validated_schedules_json")
    public void setValidatedSchedulesFromJsonString(com.fasterxml.jackson.databind.JsonNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
        applyValidatedSchedulesNode(node);
    }

    private void applyValidatedSchedulesNode(com.fasterxml.jackson.databind.JsonNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            this.validatedSchedules = java.util.Collections.emptyList();
            return;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                this.validatedSchedules = java.util.Collections.emptyList();
                return;
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.validatedSchedules = mapper.readValue(text, mapper.getTypeFactory().constructCollectionType(java.util.List.class, ValidatedScheduleDTO.class));
            return;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.validatedSchedules = mapper.readValue(node.toString(), mapper.getTypeFactory().constructCollectionType(java.util.List.class, ValidatedScheduleDTO.class));
        }
    }

    private WorkflowStatistics statistics;
    private List<Object> errors;
    private List<Object> warnings;
    private String message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowStatistics implements Serializable {
        @JsonProperty("total_schedules")
        private Integer totalSchedules;

        @JsonProperty("total_quota")
        private Integer totalQuota;

        @JsonProperty("expert_ratio")
        private BigDecimal expertRatio;

        @JsonProperty("avg_weekly_workload")
        private BigDecimal avgWeeklyWorkload;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidatedScheduleDTO implements Serializable {
        @JsonProperty("physician_id")
        private Long physicianId;

        @JsonProperty("physician_name")
        private String physicianName;

        @JsonProperty("work_date")
        private String workDate;

        @JsonProperty("time_slot")
        private String timeSlot;

        @JsonProperty("total_quota")
        private Integer totalQuota;

        @JsonProperty("available_quota")
        private Integer availableQuota;

        @JsonProperty("used_quota")
        private Integer usedQuota;

        private BigDecimal price;

        @JsonProperty("regist_level_id")
        private Long registLevelId;

        @JsonProperty("ai_suggestion")
        private String aiSuggestion;

        private String status;
    }
}
