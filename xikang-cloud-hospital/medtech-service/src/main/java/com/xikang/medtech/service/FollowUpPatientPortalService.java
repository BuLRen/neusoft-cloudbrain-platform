package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.PatientFollowUpAuthContext;
import com.xikang.medtech.mapper.FollowUpLastVisitMapper;
import com.xikang.medtech.mapper.FollowUpPatientMapper;
import com.xikang.medtech.mapper.PatientFollowUpAuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpPatientPortalService {

    private final FollowUpPatientMapper followUpPatientMapper;
    private final GlucoseForecastService glucoseForecastService;
    private final HealthObservationService healthObservationService;
    private final FollowUpLastVisitMapper followUpLastVisitMapper;
    private final PatientFollowUpAuthMapper patientFollowUpAuthMapper;
    private final FollowUpCommunicationService followUpCommunicationService;

    public List<Map<String, Object>> listPlans(Long patientId, List<Long> registerIds) {
        Long resolvedPatientId = resolvePatientId(patientId);
        List<Long> ids = resolveRegisterIds(resolvedPatientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectPlansByRegisterIds(ids);
    }

    public List<Map<String, Object>> listRecords(Long patientId, List<Long> registerIds) {
        Long resolvedPatientId = resolvePatientId(patientId);
        List<Long> ids = resolveRegisterIds(resolvedPatientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectRecordsByRegisterIds(ids);
    }

    public List<Map<String, Object>> listMedications(Long patientId, List<Long> registerIds) {
        Long resolvedPatientId = resolvePatientId(patientId);
        List<Long> ids = resolveRegisterIds(resolvedPatientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectPrescriptionsByRegisterIds(ids);
    }

    public Map<String, Object> getLastVisit(Long patientId, Long registerId) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        Map<String, Object> snapshot = followUpLastVisitMapper.selectByRegisterId(targetRegisterId);
        if (snapshot == null || snapshot.isEmpty()) {
            throw new BusinessException("暂无上次看诊记录");
        }
        return snapshot;
    }

    public List<Map<String, Object>> listObservations(
        Long patientId,
        Long registerId,
        LocalDate from,
        LocalDate to,
        String sourceType
    ) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        List<String> sourceTypes = null;
        if (sourceType == null || sourceType.isBlank()) {
            sourceTypes = List.of("patient_report", "uci_import");
        }
        return healthObservationService.getMetrics(
            targetRegisterId,
            from,
            to,
            List.of("blood_glucose"),
            sourceType,
            sourceTypes
        );
    }

    @Transactional
    public Map<String, Object> createObservation(Long patientId, Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Long resolvedPatientId = resolvePatientId(patientId);
        Long targetRegisterId = requireAccessibleRegister(resolvedPatientId, registerId);

        double metricValue = toDouble(request.get("metricValue"));
        if (metricValue <= 0 || metricValue > 33) {
            throw new BusinessException("血糖数值不合理，请输入 0~33 mmol/L 范围内的值");
        }

        LocalDateTime observedAt = parseDateTime(request.get("observedAt"));
        String note = request.get("note") != null ? String.valueOf(request.get("note")).trim() : null;

        Map<String, Object> created = healthObservationService.insertPatientReportObservation(
            targetRegisterId,
            observedAt,
            metricValue,
            note
        );

        glucoseForecastService.refreshForecastAsync(targetRegisterId);
        return created;
    }

    public Map<String, Object> getGlucoseAdvice(Long patientId, Long registerId) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        return glucoseForecastService.buildAdvice(targetRegisterId);
    }

    @Transactional
    public Map<String, Object> completePlan(Long planId) {
        if (planId == null) {
            throw new BusinessException("planId 不能为空");
        }
        followUpPatientMapper.updatePlanStatus(planId, "completed");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", planId);
        result.put("planStatus", "completed");
        return result;
    }

    @Transactional
    public Map<String, Object> submitFeedback(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Long resolvedPatientId = resolvePatientId(null);
        if (registerId != null && resolvedPatientId != null) {
            requireAccessibleRegister(resolvedPatientId, registerId);
        }
        Long followUpPlanId = toLong(request.get("followUpPlanId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }

        String symptom = request.get("symptom") != null ? String.valueOf(request.get("symptom")).trim() : "";
        String feedback = request.get("feedback") != null ? String.valueOf(request.get("feedback")).trim() : "";
        int rating = toInt(request.get("rating"), 5);

        String combined = symptom;
        if (!feedback.isEmpty()) {
            combined = combined.isEmpty() ? feedback : combined + "\n" + feedback;
        }
        if (rating > 0) {
            combined = combined + "\n[整体评价: " + rating + "/5]";
        }

        String relief = mapRatingToRelief(rating);
        if (followUpPlanId == null || followUpPlanId <= 0) {
            List<Map<String, Object>> plans = followUpPatientMapper.selectPlansByRegisterIds(List.of(registerId));
            if (plans.isEmpty()) {
                throw new BusinessException("未找到可关联的随访计划，请先选择就诊记录");
            }
            followUpPlanId = toLong(plans.get(0).get("id"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("followUpPlanId", followUpPlanId);
        payload.put("registerId", registerId);
        payload.put("symptomRelief", relief);
        payload.put("hasSideEffect", 0);
        payload.put("patientFeedback", combined);
        payload.put("aiAssessment", null);
        payload.put("followUpTime", null);

        followUpPatientMapper.insertFollowUpRecord(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", payload.get("id"));
        result.put("registerId", registerId);
        result.put("symptomRelief", relief);
        result.put("patientFeedback", combined);
        return result;
    }

    public Map<String, Object> getGlucoseForecast(Long patientId, Long registerId) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        return glucoseForecastService.getForecast(targetRegisterId, null, null);
    }

    public Map<String, Object> getCommunicationSession(Long patientId, Long registerId) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        return followUpCommunicationService.getPatientSession(targetRegisterId);
    }

    public Map<String, Object> listCommunicationMessages(
        Long patientId,
        Long registerId,
        Integer limit,
        Integer offset
    ) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        Map<String, Object> session = followUpCommunicationService.getPatientSession(targetRegisterId);
        Long sessionId = toLong(session.get("id"));
        if (sessionId == null) {
            throw new BusinessException("沟通会话不存在");
        }
        return followUpCommunicationService.listMessages(sessionId, limit, offset);
    }

    public Map<String, Object> getSharedCaseSummary(Long patientId, Long registerId) {
        Long targetRegisterId = requireAccessibleRegister(resolvePatientId(patientId), registerId);
        return followUpCommunicationService.getSharedCaseSummary(targetRegisterId);
    }

    private Long resolvePatientId(Long patientId) {
        if (patientId != null) {
            return patientId;
        }
        Long fromContext = PatientFollowUpAuthContext.primaryPatientIdOrNull();
        if (fromContext != null) {
            return fromContext;
        }
        List<Long> ids = PatientFollowUpAuthContext.patientIdsOrEmpty();
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long requireAccessibleRegister(Long patientId, Long registerId) {
        if (patientId == null) {
            throw new BusinessException("无法确定患者身份");
        }
        Long targetRegisterId = registerId;
        if (targetRegisterId == null) {
            List<Long> ids = followUpPatientMapper.selectRegisterIdsByPatientId(patientId);
            if (ids.isEmpty()) {
                throw new BusinessException("无法确定就诊记录");
            }
            targetRegisterId = ids.get(0);
        }

        List<Long> patientIds = PatientFollowUpAuthContext.patientIdsOrEmpty();
        if (patientIds.isEmpty()) {
            patientIds = List.of(patientId);
        }
        Long userId = PatientFollowUpAuthContext.userIdOrNull();
        if (!patientFollowUpAuthMapper.isRegisterAccessible(targetRegisterId, patientIds, userId)) {
            throw new BusinessException("无权访问该就诊记录");
        }
        return targetRegisterId;
    }

    private List<Long> resolveRegisterIds(Long patientId, List<Long> registerIds) {
        if (registerIds != null && !registerIds.isEmpty()) {
            return registerIds;
        }
        if (patientId == null) {
            return List.of();
        }
        return followUpPatientMapper.selectRegisterIdsByPatientId(patientId);
    }

    private String mapRatingToRelief(int rating) {
        if (rating >= 4) {
            return "relieved";
        }
        if (rating == 3) {
            return "partial";
        }
        if (rating == 2) {
            return "unchanged";
        }
        return "worsened";
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
