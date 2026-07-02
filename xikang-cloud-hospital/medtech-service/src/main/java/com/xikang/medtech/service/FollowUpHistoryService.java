package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
        List<Map<String, Object>> events = historyMapper.selectEvents(registerId, departmentId, from, to, eventType, limit);
        for (Map<String, Object> event : events) {
            enrichEventForDisplay(event);
        }
        return events;
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

    public void recordCommunicationMessage(
        Long registerId,
        Long messageId,
        String messageType,
        String content,
        Map<String, Object> extraPayload
    ) {
        String eventType = resolveCommunicationEventType(messageType);
        String normalizedContent = normalizeText(content);
        String title = buildCommunicationTitle(messageType, normalizedContent, extraPayload);
        String summary = buildCommunicationSummary(messageType, normalizedContent, extraPayload);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (extraPayload != null && !extraPayload.isEmpty()) {
            payload.putAll(extraPayload);
        }
        payload.put("messageType", messageType);
        if (normalizedContent != null) {
            payload.put("content", normalizedContent);
        }

        recordEvent(
            registerId,
            eventType,
            "nurse",
            MedtechAuthContext.employeeIdOrNull(),
            title,
            summary,
            payload,
            "follow_up_communication_message",
            messageId,
            LocalDateTime.now()
        );
    }

    @SuppressWarnings("unchecked")
    private void enrichEventForDisplay(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return;
        }
        String eventType = event.get("eventType") != null ? String.valueOf(event.get("eventType")) : "";
        Map<String, Object> payload = parsePayloadMap(event.get("payload"));
        String summary = normalizeText(event.get("summary"));
        String title = normalizeText(event.get("title"));

        if (summary == null) {
            summary = buildLegacySummary(eventType, payload);
        }
        if (title == null || "医患沟通消息".equals(title)) {
            title = buildLegacyTitle(eventType, payload, summary);
        }

        event.put("title", title);
        event.put("summary", summary);
    }

    private String resolveCommunicationEventType(String messageType) {
        if ("drug_card".equals(messageType)) {
            return "drug_card";
        }
        if ("diagnosis_card".equals(messageType)) {
            return "diagnosis_card";
        }
        if ("case_summary".equals(messageType)) {
            return "case_summary";
        }
        return "communication_message";
    }

    private String buildCommunicationTitle(String messageType, String content, Map<String, Object> extraPayload) {
        if ("case_summary".equals(messageType)) {
            return "发布病例总结";
        }
        if ("drug_card".equals(messageType)) {
            if (content != null) {
                return content;
            }
            Object drugName = extraPayload != null ? extraPayload.get("drugName") : null;
            return drugName != null ? "推荐药品：" + drugName : "发送荐药卡片";
        }
        if ("diagnosis_card".equals(messageType)) {
            if (content != null) {
                return content;
            }
            Object diseaseName = extraPayload != null ? extraPayload.get("diseaseName") : null;
            return diseaseName != null ? "可能病况：" + diseaseName : "发送病况卡片";
        }
        if ("text".equals(messageType)) {
            return "医患文字沟通";
        }
        return "随访沟通消息";
    }

    private String buildCommunicationSummary(String messageType, String content, Map<String, Object> extraPayload) {
        if (content != null) {
            return excerpt(content, 400);
        }
        if ("case_summary".equals(messageType)) {
            return "已向患者分享本次看诊病例总结，请患者在随访沟通中查看详情。";
        }
        if ("drug_card".equals(messageType) && extraPayload != null) {
            Object usage = extraPayload.get("drugUsage");
            Object drugName = extraPayload.get("drugName");
            if (drugName != null && usage != null) {
                return "推荐 " + drugName + "，" + usage;
            }
            if (drugName != null) {
                return "推荐药品：" + drugName;
            }
        }
        if ("diagnosis_card".equals(messageType) && extraPayload != null) {
            Object diseaseName = extraPayload.get("diseaseName");
            Object treatment = extraPayload.get("treatmentDirection");
            if (diseaseName != null && treatment != null) {
                return "可能病况：" + diseaseName + "，建议 " + treatment;
            }
            if (diseaseName != null) {
                return "可能病况：" + diseaseName;
            }
        }
        return "随访沟通消息已发送。";
    }

    private String buildLegacyTitle(String eventType, Map<String, Object> payload, String summary) {
        String messageType = payload.get("messageType") != null ? String.valueOf(payload.get("messageType")) : eventType;
        return buildCommunicationTitle(messageType, normalizeText(payload.get("content")), payload);
    }

    private String buildLegacySummary(String eventType, Map<String, Object> payload) {
        String content = normalizeText(payload.get("content"));
        if (content != null) {
            return excerpt(content, 400);
        }
        content = normalizeText(payload.get("summary"));
        if (content != null) {
            return excerpt(content, 400);
        }
        if ("glucose_entry".equals(eventType) && payload.get("metricValue") != null) {
            String note = normalizeText(payload.get("note"));
            String base = String.format("录入血糖 %s mmol/L", payload.get("metricValue"));
            return note != null ? base + "（" + note + "）" : base;
        }
        if ("patient_feedback".equals(eventType)) {
            Object rating = payload.get("rating");
            String relief = normalizeText(payload.get("symptomRelief"));
            if (rating != null && relief != null) {
                return "患者反馈：整体感受 " + rating + "/5，症状变化 " + relief;
            }
        }
        return buildCommunicationSummary(
            payload.get("messageType") != null ? String.valueOf(payload.get("messageType")) : eventType,
            null,
            payload
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayloadMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private String excerpt(String text, int maxLen) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "…";
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
