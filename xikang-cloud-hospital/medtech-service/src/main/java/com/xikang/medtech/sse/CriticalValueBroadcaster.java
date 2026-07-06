package com.xikang.medtech.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.entity.CriticalValueAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalValueBroadcaster {

    private final CriticalValueSubscriberRegistry registry;
    private final ObjectMapper objectMapper;

    public void broadcastNew(CriticalValueAlert alert) {
        broadcast("CRITICAL_NEW", alert);
    }

    public void broadcastEscalated(CriticalValueAlert alert) {
        broadcast("CRITICAL_ESCALATED", alert);
    }

    public void broadcastClosed(CriticalValueAlert alert) {
        broadcast("CRITICAL_CLOSED", alert);
    }

    private void broadcast(String eventType, CriticalValueAlert alert) {
        String json = serialize(buildPayload(eventType, alert));
        if (json == null) {
            return;
        }
        if (alert.getDoctorId() != null) {
            sendToTopic("critical:doctor:" + alert.getDoctorId(), eventType, json);
        }
        sendToTopic("critical:board", eventType, json);
    }

    private void sendToTopic(String topic, String eventType, String json) {
        Set<SseEmitter> emitters = registry.getEmitters(topic);
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(json));
            } catch (Exception ex) {
                log.warn("[CriticalSSE] push failed topic={}: {}", topic, ex.getMessage());
                emitter.completeWithError(ex);
            }
        }
    }

    private Map<String, Object> buildPayload(String type, CriticalValueAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("alertId", alert.getId());
        payload.put("registerId", alert.getRegisterId());
        payload.put("patientName", alert.getPatientName());
        payload.put("caseNumber", alert.getCaseNumber());
        payload.put("sourceType", alert.getSourceType());
        payload.put("sourceId", alert.getSourceId());
        payload.put("techName", alert.getTechName());
        payload.put("criticalItems", alert.getCriticalItems());
        payload.put("severity", alert.getSeverity());
        payload.put("status", alert.getStatus());
        payload.put("doctorId", alert.getDoctorId());
        payload.put("doctorName", alert.getDoctorName());
        payload.put("reporterName", alert.getReporterName());
        payload.put("reportedTime", alert.getReportedTime());
        payload.put("ackDeadline", alert.getAckDeadline());
        payload.put("escalatedTime", alert.getEscalatedTime());
        return payload;
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("[CriticalSSE] serialize failed: {}", ex.getMessage());
            return null;
        }
    }
}
