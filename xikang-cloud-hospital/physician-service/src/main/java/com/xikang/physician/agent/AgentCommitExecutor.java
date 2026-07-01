package com.xikang.physician.agent;

import com.xikang.physician.service.ClinicalRecordService;
import com.xikang.physician.service.PhysicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes high-risk Agent commit actions after doctor confirmation.
 */
@Service
@RequiredArgsConstructor
public class AgentCommitExecutor {

    private final PhysicianService physicianService;
    private final ClinicalRecordService clinicalRecordService;

    @Transactional
    public Map<String, Object> execute(String actionType, Long registerId, Map<String, Object> payload) {
        return switch (actionType) {
            case "commit_medical_record" -> commitMedicalRecord(registerId, payload);
            case "commit_preliminary_diagnosis" -> commitPreliminaryDiagnosis(registerId, payload);
            case "commit_check_requests" -> commitTechnologyRequests(registerId, payload, "check");
            case "commit_inspection_requests" -> commitTechnologyRequests(registerId, payload, "inspection");
            case "commit_disposal_requests" -> commitTechnologyRequests(registerId, payload, "disposal");
            case "commit_diagnosis" -> commitDiagnosis(registerId, payload);
            case "commit_prescription" -> commitPrescription(registerId, payload);
            case "commit_archive_visit" -> commitArchive(registerId);
            default -> throw new IllegalArgumentException("不支持的提交操作: " + actionType);
        };
    }

    private Map<String, Object> commitMedicalRecord(Long registerId, Map<String, Object> payload) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        copyIfPresent(payload, request, "readme", "present", "presentTreat", "history", "allergy", "physique", "proposal");
        if (payload.get("diseaseIds") != null) {
            request.put("diseaseIds", payload.get("diseaseIds"));
        }

        Map<String, Object> existing = physicianService.getMedicalRecord(registerId);
        Map<String, Object> result;
        if (existing != null && existing.get("id") != null) {
            Long id = toLong(existing.get("id"));
            physicianService.updateMedicalRecord(id, request);
            result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("updated", true);
        } else {
            result = physicianService.createMedicalRecord(request);
            result.put("updated", false);
        }
        result.put("registerId", registerId);
        return result;
    }

    private Map<String, Object> commitPreliminaryDiagnosis(Long registerId, Map<String, Object> payload) {
        String diagnosis = text(payload.get("preliminaryDiagnosis"));
        if (diagnosis == null) {
            diagnosis = text(payload.get("preliminary_diagnosis"));
        }
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new IllegalArgumentException("preliminaryDiagnosis 不能为空");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        request.put("preliminaryDiagnosis", diagnosis.trim());
        if (payload.get("diseaseIds") != null) {
            request.put("diseaseIds", payload.get("diseaseIds"));
        }
        if (payload.get("suggestedDiseaseNames") != null) {
            request.put("suggestedDiseaseNames", payload.get("suggestedDiseaseNames"));
        }
        physicianService.savePreliminaryDiagnosis(request);
        return Map.of("registerId", registerId, "preliminaryDiagnosis", diagnosis.trim(), "saved", true);
    }

    private Map<String, Object> commitTechnologyRequests(Long registerId, Map<String, Object> payload, String type) {
        List<Map<String, Object>> items = extractItems(payload);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        request.put("items", items);
        Map<String, Object> result = switch (type) {
            case "check" -> physicianService.createCheckRequest(request);
            case "inspection" -> physicianService.createInspectionRequest(request);
            case "disposal" -> physicianService.createDisposalRequest(request);
            default -> throw new IllegalArgumentException("未知医技类型: " + type);
        };
        result.put("type", type);
        result.put("itemCount", items.size());
        return result;
    }

    private Map<String, Object> commitDiagnosis(Long registerId, Map<String, Object> payload) {
        Map<String, Object> record = physicianService.getMedicalRecord(registerId);
        if (record == null || record.get("id") == null) {
            throw new IllegalArgumentException("请先完善病历后再提交确诊");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        request.put("medicalRecordId", toLong(record.get("id")));
        request.put("diagnosis", text(payload.get("diagnosis")));
        request.put("cure", text(payload.get("cure")));
        request.put("careful", text(payload.get("careful")));
        if (payload.get("diseaseIds") != null) {
            request.put("diseaseIds", payload.get("diseaseIds"));
        }
        physicianService.submitDiagnosis(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("diagnosis", request.get("diagnosis"));
        result.put("saved", true);
        return result;
    }

    private Map<String, Object> commitPrescription(Long registerId, Map<String, Object> payload) {
        List<Map<String, Object>> items = extractItems(payload);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("处方 items 不能为空");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        request.put("confirmedDiagnosis", text(payload.get("confirmedDiagnosis")));
        request.put("items", items);
        Map<String, Object> result = physicianService.createPrescription(request);
        result.put("itemCount", items.size());
        return result;
    }

    private Map<String, Object> commitArchive(Long registerId) {
        return clinicalRecordService.archiveVisit(registerId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> payload) {
        Object items = payload.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String... keys) {
        for (String key : keys) {
            if (from.containsKey(key) && from.get(key) != null) {
                to.put(key, from.get(key));
            }
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
