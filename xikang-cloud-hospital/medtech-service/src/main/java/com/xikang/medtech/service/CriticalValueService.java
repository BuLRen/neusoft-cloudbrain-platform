package com.xikang.medtech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.CriticalValueAuthContext;
import com.xikang.medtech.critical.CriticalDetectResult;
import com.xikang.medtech.critical.CriticalItemHit;
import com.xikang.medtech.entity.CriticalValueAlert;
import com.xikang.medtech.mapper.CriticalValueAlertMapper;
import com.xikang.medtech.mapper.CriticalValueContextMapper;
import com.xikang.medtech.sse.CriticalValueBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CriticalValueService {

    private static final int ACK_TIMEOUT_MINUTES = 10;
    private static final int BOARD_LIMIT = 200;

    private final CriticalValueAlertMapper alertMapper;
    private final CriticalValueContextMapper contextMapper;
    private final CriticalValueBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public Map<String, Object> toDetectMap(CriticalDetectResult detect) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("suspected", detect.isSuspected());
        map.put("severity", detect.getSeverity());
        map.put("detectSource", detect.getDetectSource());
        map.put("items", detect.getItems());
        return map;
    }

    @Transactional
    public Map<String, Object> report(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        String sourceType = stringValue(request.get("sourceType"));
        Long sourceId = toLong(request.get("sourceId"));
        if (registerId == null || sourceType == null || sourceId == null) {
            throw new BusinessException(400, "缺少危急值上报参数");
        }

        Map<String, Object> registerInfo = contextMapper.selectRegisterDoctor(registerId);
        if (registerInfo == null) {
            throw new BusinessException(404, "就诊记录不存在");
        }

        Long doctorId = toLong(registerInfo.get("doctorId"));
        if (doctorId == null) {
            throw new BusinessException(400, "该就诊未绑定接诊医生，无法上报危急值");
        }

        List<CriticalItemHit> items = parseItems(request.get("items"));
        if (items.isEmpty()) {
            throw new BusinessException(400, "危急值明细不能为空");
        }

        CriticalValueAuthContext.Context reporterCtx = CriticalValueAuthContext.get();
        LocalDateTime now = LocalDateTime.now();

        CriticalValueAlert alert = new CriticalValueAlert();
        alert.setRegisterId(registerId);
        alert.setPatientName(stringValue(registerInfo.get("patientName")));
        alert.setCaseNumber(stringValue(registerInfo.get("caseNumber")));
        alert.setSourceType(sourceType);
        alert.setSourceId(sourceId);
        alert.setTechName(stringValue(request.get("techName")));
        alert.setCriticalItems(writeJson(items));
        alert.setSeverity(stringValue(request.get("severity")) != null ? stringValue(request.get("severity")) : "CRITICAL");
        alert.setReporterId(reporterCtx != null ? reporterCtx.employeeId() : null);
        alert.setReporterName(reporterCtx != null ? reporterCtx.realName() : null);
        alert.setDoctorId(doctorId);
        alert.setDoctorName(stringValue(registerInfo.get("doctorName")));
        alert.setStatus("PENDING");
        alert.setReportedTime(now);
        alert.setAckDeadline(now.plusMinutes(ACK_TIMEOUT_MINUTES));

        alertMapper.insert(alert);
        broadcaster.broadcastNew(alert);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("alertId", alert.getId());
        response.put("status", alert.getStatus());
        response.put("ackDeadline", alert.getAckDeadline());
        return response;
    }

    public List<Map<String, Object>> listPending(Long doctorId) {
        assertPhysicianDoctorAccess(doctorId);
        List<CriticalValueAlert> alerts = alertMapper.selectPendingByDoctorId(doctorId);
        return alerts.stream().map(this::toAlertMap).toList();
    }

    @Transactional
    public Map<String, Object> acknowledge(Long alertId) {
        CriticalValueAlert alert = requireAlert(alertId);
        assertPhysicianDoctorAccess(alert.getDoctorId());
        if (!"PENDING".equals(alert.getStatus()) && !"ESCALATED".equals(alert.getStatus())) {
            throw new BusinessException(400, "当前状态不可签收");
        }
        LocalDateTime now = LocalDateTime.now();
        alertMapper.updateAcknowledged(alertId, now, "ACKNOWLEDGED");
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedTime(now);
        return toAlertMap(alert);
    }

    @Transactional
    public Map<String, Object> handle(Long alertId, Map<String, Object> request) {
        CriticalValueAlert alert = requireAlert(alertId);
        assertPhysicianDoctorAccess(alert.getDoctorId());
        String handleNote = stringValue(request.get("handleNote"));
        if (handleNote == null || handleNote.isBlank()) {
            throw new BusinessException(400, "请填写处置意见");
        }
        if ("HANDLED".equals(alert.getStatus()) || "CLOSED".equals(alert.getStatus())) {
            throw new BusinessException(400, "该危急值已处置");
        }

        LocalDateTime now = LocalDateTime.now();
        if (alert.getAcknowledgedTime() == null) {
            alertMapper.updateAcknowledged(alertId, now, "ACKNOWLEDGED");
        }
        alertMapper.updateHandled(alertId, now, handleNote.trim(), "HANDLED");
        alert.setStatus("HANDLED");
        alert.setHandledTime(now);
        alert.setHandleNote(handleNote.trim());
        broadcaster.broadcastClosed(alert);
        return toAlertMap(alert);
    }

    @Transactional
    public void escalateOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<CriticalValueAlert> overdue = alertMapper.selectOverduePending(now);
        for (CriticalValueAlert alert : overdue) {
            try {
                alertMapper.updateEscalated(alert.getId(), now, "ESCALATED");
                alert.setStatus("ESCALATED");
                alert.setEscalatedTime(now);
                broadcaster.broadcastEscalated(alert);
                log.warn("[CriticalValue] escalated alertId={} doctorId={}", alert.getId(), alert.getDoctorId());
            } catch (Exception ex) {
                log.warn("[CriticalValue] escalate failed alertId={}: {}", alert.getId(), ex.getMessage());
            }
        }
    }

    public Map<String, Object> board() {
        List<CriticalValueAlert> alerts = alertMapper.selectBoardAlerts(BOARD_LIMIT);
        Map<String, Object> rawStats = alertMapper.selectBoardStats();
        Map<String, Object> stats = normalizeBoardStats(rawStats);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("alerts", alerts.stream().map(this::toAlertMap).toList());
        response.put("stats", stats);
        return response;
    }

    private Map<String, Object> normalizeBoardStats(Map<String, Object> raw) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (raw == null) {
            return stats;
        }
        long pending = longValueOrZero(raw.get("pendingCount"));
        long escalated = longValueOrZero(raw.get("escalatedCount"));
        long handled = longValueOrZero(raw.get("handledCount"));
        long total = pending + escalated + handled;
        stats.put("pendingCount", pending);
        stats.put("escalatedCount", escalated);
        stats.put("handledCount", handled);
        stats.put("overdueRate", total == 0 ? 0.0 : (double) escalated / total);
        stats.put("avgAckMinutes", secondsToMinutes(raw.get("avgAckSeconds")));
        stats.put("avgHandleMinutes", secondsToMinutes(raw.get("avgHandleSeconds")));
        return stats;
    }

    private long longValueOrZero(Object value) {
        Long parsed = toLong(value);
        return parsed == null ? 0L : parsed;
    }

    private double secondsToMinutes(Object seconds) {
        if (seconds instanceof Number number) {
            return number.doubleValue() / 60.0;
        }
        return 0.0;
    }

    private CriticalValueAlert requireAlert(Long alertId) {
        CriticalValueAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(404, "危急值工单不存在");
        }
        return alert;
    }

    private void assertPhysicianDoctorAccess(Long doctorId) {
        if (CriticalValueAuthContext.isAdmin()) {
            return;
        }
        Long currentDoctorId = CriticalValueAuthContext.employeeIdOrNull();
        if (currentDoctorId == null || doctorId == null || !currentDoctorId.equals(doctorId)) {
            throw new BusinessException(403, "无权操作该医生的危急值工单");
        }
    }

    private Map<String, Object> toAlertMap(CriticalValueAlert alert) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", alert.getId());
        map.put("registerId", alert.getRegisterId());
        map.put("patientName", alert.getPatientName());
        map.put("caseNumber", alert.getCaseNumber());
        map.put("sourceType", alert.getSourceType());
        map.put("sourceId", alert.getSourceId());
        map.put("techName", alert.getTechName());
        map.put("criticalItems", parseJsonItems(alert.getCriticalItems()));
        map.put("severity", alert.getSeverity());
        map.put("reporterId", alert.getReporterId());
        map.put("reporterName", alert.getReporterName());
        map.put("doctorId", alert.getDoctorId());
        map.put("doctorName", alert.getDoctorName());
        map.put("status", alert.getStatus());
        map.put("reportedTime", alert.getReportedTime());
        map.put("acknowledgedTime", alert.getAcknowledgedTime());
        map.put("handledTime", alert.getHandledTime());
        map.put("handleNote", alert.getHandleNote());
        map.put("escalatedTime", alert.getEscalatedTime());
        map.put("ackDeadline", alert.getAckDeadline());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<CriticalItemHit> parseItems(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            if (raw instanceof List<?> list) {
                List<CriticalItemHit> items = new ArrayList<>();
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map) {
                        CriticalItemHit hit = objectMapper.convertValue(map, CriticalItemHit.class);
                        items.add(hit);
                    }
                }
                return items;
            }
            return objectMapper.convertValue(raw, objectMapper.getTypeFactory().constructCollectionType(List.class, CriticalItemHit.class));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "危急值明细格式不正确");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "危急值明细序列化失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
