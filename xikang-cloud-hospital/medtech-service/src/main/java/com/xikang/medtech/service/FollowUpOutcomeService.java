package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpLastVisitMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import com.xikang.medtech.mapper.RevisitRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpOutcomeService {

    private final FollowUpOutcomeMapper followUpOutcomeMapper;
    private final HealthObservationService healthObservationService;
    private final FollowUpLastVisitMapper followUpLastVisitMapper;
    private final RevisitRequestMapper revisitRequestMapper;
    private final GlucoseForecastService glucoseForecastService;

    public List<Map<String, Object>> listPatients(Integer visitState) {
        List<Map<String, Object>> patients = followUpOutcomeMapper.selectFollowUpPatients(visitState);
        for (Map<String, Object> patient : patients) {
            Long registerId = toLong(patient.get("registerId"));
            if (registerId != null) {
                patient.put("diseases", followUpOutcomeMapper.selectPatientDiseases(registerId));
            }
        }
        return patients;
    }

    public Map<String, Object> getProfile(Long registerId) {
        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        if (profile == null || profile.isEmpty()) {
            throw new BusinessException("未找到该挂号患者");
        }
        List<Map<String, Object>> diseases = followUpOutcomeMapper.selectPatientDiseases(registerId);
        profile.put("diseases", diseases);
        profile.put("primaryDiseaseCategory", resolvePrimaryCategory(diseases));
        return profile;
    }

    public Map<String, Object> getPatientDetail(Long registerId) {
        Map<String, Object> detail = followUpOutcomeMapper.selectPatientDetail(registerId);
        if (detail == null || detail.isEmpty()) {
            throw new BusinessException("未找到该挂号患者");
        }
        List<Map<String, Object>> diseases = followUpOutcomeMapper.selectPatientDiseases(registerId);
        detail.put("diseases", diseases);
        if (detail.get("allergy") == null && detail.get("consultationAllergy") != null) {
            detail.put("allergy", detail.get("consultationAllergy"));
        }
        detail.remove("consultationAllergy");
        return detail;
    }

    public List<Map<String, Object>> getMetrics(Long registerId, LocalDate from, LocalDate to, List<String> metricKeys) {
        return healthObservationService.getMetrics(registerId, from, to, metricKeys);
    }

    public List<Map<String, Object>> getMetrics(
        Long registerId,
        LocalDate from,
        LocalDate to,
        List<String> metricKeys,
        String sourceType
    ) {
        return healthObservationService.getMetrics(registerId, from, to, metricKeys, sourceType, null);
    }

    public Map<String, Object> getLastVisit(Long registerId) {
        Map<String, Object> snapshot = followUpLastVisitMapper.selectByRegisterId(registerId);
        if (snapshot == null || snapshot.isEmpty()) {
            throw new BusinessException("暂无上次看诊快照");
        }
        return snapshot;
    }

    public List<Map<String, Object>> listRevisitRequests(Long departmentId) {
        return revisitRequestMapper.selectPending(departmentId);
    }

    public int countPendingRevisitRequests(Long registerId) {
        return revisitRequestMapper.countPendingByRegisterId(registerId);
    }

    public Map<String, Object> getGlucoseAdvice(Long registerId) {
        return glucoseForecastService.buildAdvice(registerId);
    }

    public List<Map<String, Object>> getRecords(Long registerId) {
        return followUpOutcomeMapper.selectFollowUpRecords(registerId);
    }

    public List<Map<String, Object>> listInterviewSchedules(LocalDate weekStart, String status) {
        return followUpOutcomeMapper.selectInterviewSchedules(weekStart, status);
    }

    @Transactional
    public Map<String, Object> createInterviewSchedule(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }

        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        if (profile == null || profile.isEmpty()) {
            throw new BusinessException("未找到该挂号患者");
        }

        LocalDate weekStartDate = parseDate(request.get("weekStartDate"));
        if (weekStartDate == null) {
            weekStartDate = startOfWeek(LocalDate.now());
        } else {
            weekStartDate = startOfWeek(weekStartDate);
        }

        Map<String, Object> existing = followUpOutcomeMapper.selectInterviewScheduleByRegisterAndWeek(registerId, weekStartDate);
        if (existing != null && !existing.isEmpty()) {
            throw new BusinessException("该患者本周访谈日程已存在");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("caseNumber", profile.get("caseNumber"));
        payload.put("patientName", profile.get("realName"));
        payload.put("weekStartDate", weekStartDate);
        payload.put("status", "scheduled");
        payload.put("triggerReason", request.get("triggerReason"));
        payload.put("triggerMetricKey", request.get("triggerMetricKey"));
        payload.put("createdBy", MedtechAuthContext.employeeIdOrNull());

        followUpOutcomeMapper.insertInterviewSchedule(payload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", payload.get("id"));
        result.put("registerId", registerId);
        result.put("caseNumber", profile.get("caseNumber"));
        result.put("patientName", profile.get("realName"));
        result.put("weekStartDate", weekStartDate.toString());
        result.put("status", "scheduled");
        result.put("triggerReason", request.get("triggerReason"));
        result.put("triggerMetricKey", request.get("triggerMetricKey"));
        result.put("patientNotified", 0);
        return result;
    }

    public Map<String, Object> getCurrentWeekScheduleStatus(Long registerId) {
        LocalDate weekStart = startOfWeek(LocalDate.now());
        Map<String, Object> existing = followUpOutcomeMapper.selectInterviewScheduleByRegisterAndWeek(registerId, weekStart);
        if (existing == null || existing.isEmpty()) {
            return Map.of("scheduled", false, "weekStartDate", weekStart.toString());
        }
        Map<String, Object> result = new LinkedHashMap<>(existing);
        result.put("scheduled", true);
        return result;
    }

    private String resolvePrimaryCategory(List<Map<String, Object>> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return "default";
        }
        Object category = diseases.get(0).get("diseaseCategory");
        return category != null ? String.valueOf(category) : "default";
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
