package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.mapper.FollowUpPatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpPatientPortalService {

    private final FollowUpPatientMapper followUpPatientMapper;

    public List<Map<String, Object>> listPlans(Long patientId, List<Long> registerIds) {
        List<Long> ids = resolveRegisterIds(patientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectPlansByRegisterIds(ids);
    }

    public List<Map<String, Object>> listRecords(Long patientId, List<Long> registerIds) {
        List<Long> ids = resolveRegisterIds(patientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectRecordsByRegisterIds(ids);
    }

    public List<Map<String, Object>> listMedications(Long patientId, List<Long> registerIds) {
        List<Long> ids = resolveRegisterIds(patientId, registerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return followUpPatientMapper.selectPrescriptionsByRegisterIds(ids);
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
