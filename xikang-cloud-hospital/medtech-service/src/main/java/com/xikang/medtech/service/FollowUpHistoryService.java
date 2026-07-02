package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpHistoryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpHistoryMapper historyMapper;

    public List<Map<String, Object>> listEvents(
        Long registerId,
        Long departmentIdOverride,
        LocalDate from,
        LocalDate to,
        String eventType,
        Integer limit
    ) {
        Long departmentId = resolveDepartmentId(departmentIdOverride);
        return historyMapper.selectEvents(registerId, departmentId, from, to, eventType, limit);
    }

    public List<Map<String, Object>> listRecentFeedbackAsRecords(Long registerId, int limit) {
        return historyMapper.selectRecentPatientFeedback(registerId, limit);
    }

    public Map<String, Object> recordEvent(
        Long registerId,
        String eventType,
        String actorType,
        Long actorId,
        String title,
        String summary,
        Map<String, Object> payload,
        String refTable,
        Long refId,
        LocalDateTime occurredAt
    ) {
        Long departmentId = historyMapper.selectRegisterDepartmentId(registerId);
        if (departmentId == null) {
            log.warn("无法确定科室，跳过历史事件 registerId={} type={}", registerId, eventType);
            return Map.of();
        }

        Map<String, Object> row = new HashMap<>();
        row.put("registerId", registerId);
        row.put("departmentId", departmentId);
        row.put("eventType", eventType);
        row.put("actorType", actorType != null ? actorType : "system");
        row.put("actorId", actorId);
        row.put("title", title);
        row.put("summary", summary);
        row.put("payloadJson", toJson(payload));
        row.put("refTable", refTable);
        row.put("refId", refId);
        row.put("occurredAt", occurredAt != null ? occurredAt : LocalDateTime.now());
        historyMapper.insertEvent(row);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("registerId", registerId);
        result.put("eventType", eventType);
        result.put("title", title);
        result.put("occurredAt", row.get("occurredAt"));
        return result;
    }

    public Map<String, Object> recordPatientFeedback(Long registerId, Long patientId, String feedback, String symptomRelief, int rating) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientFeedback", feedback);
        payload.put("symptomRelief", symptomRelief);
        payload.put("rating", rating);
        return recordEvent(
            registerId,
            "patient_feedback",
            "patient",
            patientId,
            "患者随访反馈",
            feedback,
            payload,
            null,
            null,
            LocalDateTime.now()
        );
    }

    public void recordGlucoseEntry(Long registerId, Long patientId, double metricValue, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metricKey", "blood_glucose");
        payload.put("metricValue", metricValue);
        payload.put("unit", "mmol/L");
        payload.put("note", note);
        recordEvent(
            registerId,
            "glucose_entry",
            patientId != null ? "patient" : "system",
            patientId,
            "居家血糖录入",
            String.format("血糖 %.1f mmol/L", metricValue),
            payload,
            "patient_health_observation",
            null,
            LocalDateTime.now()
        );
    }

    public void recordObservationConfirmed(Long registerId, Long nurseId, String note) {
        recordEvent(
            registerId,
            "observation_confirmed",
            "nurse",
            nurseId,
            "今日观察已确认",
            note,
            Map.of("note", note != null ? note : ""),
            "follow_up_daily_observation",
            null,
            LocalDateTime.now()
        );
    }

    public void recordInterviewScheduled(Long registerId, Long nurseId, String reason) {
        recordEvent(
            registerId,
            "interview_scheduled",
            "nurse",
            nurseId,
            "访谈已安排",
            reason,
            Map.of("triggerReason", reason != null ? reason : ""),
            "follow_up_interview_schedule",
            null,
            LocalDateTime.now()
        );
    }

    public void recordCommunicationMessage(Long registerId, Long messageId, String messageType, String summary) {
        String eventType = "drug_card".equals(messageType) ? "drug_card"
            : "diagnosis_card".equals(messageType) ? "diagnosis_card"
            : "case_summary".equals(messageType) ? "case_summary"
            : "communication_message";
        recordEvent(
            registerId,
            eventType,
            "nurse",
            MedtechAuthContext.employeeIdOrNull(),
            summary != null ? summary : "医患沟通消息",
            summary,
            Map.of("messageType", messageType),
            "follow_up_communication_message",
            messageId,
            LocalDateTime.now()
        );
    }

    private Long resolveDepartmentId(Long override) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return override;
        }
        return MedtechAuthContext.departmentIdOrNull();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload != null ? payload : Map.of());
        } catch (Exception ex) {
            return "{}";
        }
    }
}
