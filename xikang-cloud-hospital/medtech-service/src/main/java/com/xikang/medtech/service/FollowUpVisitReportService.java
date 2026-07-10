package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpVisitReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpVisitReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpVisitReportMapper visitReportMapper;
    private final FollowUpClinicalSnapshotService clinicalSnapshotService;
    private final FollowUpHistoryService historyService;

    public Map<String, Object> getLatest(Long registerId) {
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        Map<String, Object> row = visitReportMapper.selectLatestByRegisterId(registerId);
        if (row == null || row.isEmpty()) {
            return Map.of("exists", false, "registerId", registerId);
        }
        return mapReport(row);
    }

    @Transactional
    public Map<String, Object> saveDraft(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }

        Map<String, Object> snapshot = clinicalSnapshotService.getOrSyncLastVisit(registerId);
        Long medicalRecordId = toLong(snapshot.get("sourceMedicalRecordId"));
        if (medicalRecordId == null) {
            medicalRecordId = toLong(snapshot.get("medicalRecordId"));
        }

        Long existingId = toLong(request.get("id"));
        if (existingId != null) {
            Map<String, Object> existing = visitReportMapper.selectById(existingId);
            if (existing == null || existing.isEmpty()) {
                throw new BusinessException("报告不存在");
            }
            if (!"draft".equals(String.valueOf(existing.get("status")))) {
                throw new BusinessException("已定稿报告不可修改");
            }
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("id", existingId);
            update.put("observationText", request.get("observationText"));
            update.put("conclusionText", request.get("conclusionText"));
            update.put("recoveryStatus", request.getOrDefault("recoveryStatus", "unknown"));
            update.put("sourceMedicalRecordId", medicalRecordId);
            update.put("lastVisitSnapshotJson", toJson(snapshot));
            visitReportMapper.updateReport(update);
            return mapReport(visitReportMapper.selectById(existingId));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registerId", registerId);
        payload.put("sourceMedicalRecordId", medicalRecordId);
        payload.put("reportDate", request.get("reportDate") != null ? request.get("reportDate") : LocalDate.now());
        payload.put("status", "draft");
        payload.put("lastVisitSnapshotJson", toJson(snapshot));
        payload.put("observationText", request.get("observationText"));
        payload.put("conclusionText", request.get("conclusionText"));
        payload.put("recoveryStatus", request.getOrDefault("recoveryStatus", "unknown"));
        payload.put("generatedBy", MedtechAuthContext.employeeIdOrNull());
        visitReportMapper.insertReport(payload);
        return mapReport(visitReportMapper.selectById(toLong(payload.get("id"))));
    }

    @Transactional
    public Map<String, Object> finalizeReport(Long id) {
        if (id == null) {
            throw new BusinessException("报告 id 不能为空");
        }
        Map<String, Object> existing = visitReportMapper.selectById(id);
        if (existing == null || existing.isEmpty()) {
            throw new BusinessException("报告不存在");
        }
        if (!"draft".equals(String.valueOf(existing.get("status")))) {
            return mapReport(existing);
        }

        Long generatedBy = MedtechAuthContext.employeeIdOrNull();
        int updated = visitReportMapper.finalizeReport(id, generatedBy);
        if (updated == 0) {
            throw new BusinessException("定稿失败，报告可能已被处理");
        }

        Map<String, Object> finalized = visitReportMapper.selectById(id);
        Long registerId = toLong(finalized.get("registerId"));
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("reportId", id);
        eventPayload.put("recoveryStatus", finalized.get("recoveryStatus"));
        eventPayload.put("observationText", finalized.get("observationText"));
        eventPayload.put("conclusionText", finalized.get("conclusionText"));

        String recoveryLabel = recoveryLabel(String.valueOf(finalized.get("recoveryStatus")));
        String summary = finalized.get("conclusionText") != null
            ? String.valueOf(finalized.get("conclusionText"))
            : "随访报告已归档，恢复评价：" + recoveryLabel;

        historyService.recordEvent(
            registerId,
            "follow_up_report",
            "nurse",
            generatedBy,
            "随访疗效报告",
            summary,
            eventPayload,
            "follow_up_visit_report",
            id,
            LocalDateTime.now()
        );
        return mapReport(finalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapReport(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("id", row.get("id"));
        result.put("registerId", row.get("registerId"));
        result.put("sourceMedicalRecordId", row.get("sourceMedicalRecordId"));
        result.put("reportDate", row.get("reportDate"));
        result.put("status", row.get("status"));
        result.put("observationText", row.get("observationText"));
        result.put("conclusionText", row.get("conclusionText"));
        result.put("recoveryStatus", row.get("recoveryStatus"));
        result.put("generatedBy", row.get("generatedBy"));
        result.put("finalizedAt", row.get("finalizedAt"));
        result.put("creationTime", row.get("creationTime"));

        Object snapshotRaw = row.get("lastVisitSnapshotJson");
        if (snapshotRaw != null) {
            try {
                if (snapshotRaw instanceof Map<?, ?> map) {
                    result.put("lastVisitSnapshot", map);
                } else {
                    result.put(
                        "lastVisitSnapshot",
                        MAPPER.readValue(String.valueOf(snapshotRaw), new TypeReference<Map<String, Object>>() {})
                    );
                }
            } catch (Exception ex) {
                log.warn("解析 lastVisitSnapshotJson 失败: {}", ex.getMessage());
            }
        }
        return result;
    }

    private String recoveryLabel(String status) {
        return switch (status) {
            case "improved" -> "改善";
            case "stable" -> "稳定";
            case "worsened" -> "加重";
            default -> "待评估";
        };
    }

    private String toJson(Map<String, Object> snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot != null ? snapshot : Map.of());
        } catch (Exception ex) {
            return "{}";
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
}
