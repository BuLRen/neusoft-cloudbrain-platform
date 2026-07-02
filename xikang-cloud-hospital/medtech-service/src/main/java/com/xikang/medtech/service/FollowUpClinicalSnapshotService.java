package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.constants.FollowUpDepartmentConstants;
import com.xikang.medtech.mapper.FollowUpClinicalMapper;
import com.xikang.medtech.mapper.FollowUpLastVisitMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpClinicalSnapshotService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private final FollowUpClinicalMapper clinicalMapper;
    private final FollowUpLastVisitMapper lastVisitMapper;
    private final FollowUpOutcomeMapper outcomeMapper;

    public Map<String, Object> getOrSyncLastVisit(Long registerId) {
        Map<String, Object> snapshot = lastVisitMapper.selectByRegisterId(registerId);
        Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
        boolean hasMedicalRecord = medicalRecord != null && !medicalRecord.isEmpty();

        if ((snapshot == null || snapshot.isEmpty()) && hasMedicalRecord) {
            try {
                syncFromClinical(registerId);
                snapshot = lastVisitMapper.selectByRegisterId(registerId);
            } catch (Exception ex) {
                if (snapshot != null && !snapshot.isEmpty()) {
                    return finalizeSnapshot(registerId, enrichSnapshot(registerId, snapshot));
                }
                throw ex;
            }
        }

        Map<String, Object> enriched = enrichSnapshot(registerId, snapshot != null ? snapshot : Map.of());
        return finalizeSnapshot(registerId, enriched);
    }

    @Transactional
    public Map<String, Object> syncFromClinical(Long registerId) {
        Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
        if (medicalRecord == null || medicalRecord.isEmpty()) {
            return emptySnapshot(registerId);
        }

        List<Map<String, Object>> prescriptions = clinicalMapper.selectPrescriptionsByRegisterId(registerId);
        List<Map<String, Object>> diseases = outcomeMapper.selectPatientDiseases(registerId);

        String diagnosisSummary = buildDiagnosisSummary(medicalRecord, diseases);
        List<Map<String, Object>> prescriptionSummary = buildPrescriptionSummary(prescriptions);

        Map<String, Object> existing = lastVisitMapper.selectByRegisterId(registerId);
        String metricsJson = resolveProfessionalMetricsJson(existing, registerId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registerId", registerId);
        payload.put("visitDate", existing != null && existing.get("visitDate") != null
            ? existing.get("visitDate")
            : LocalDate.now().minusDays(14));
        payload.put("diagnosisSummary", diagnosisSummary);
        payload.put("professionalMetricsJson", metricsJson);
        payload.put("doctorName", existing != null && existing.get("doctorName") != null
            ? existing.get("doctorName")
            : "主治医师");
        payload.put("departmentName", existing != null && existing.get("departmentName") != null
            ? existing.get("departmentName")
            : "");
        payload.put("sourceMedicalRecordId", medicalRecord.get("id"));
        payload.put("prescriptionSummaryJson", toJson(prescriptionSummary.isEmpty() && existing != null
            ? parseJsonList(existing.get("prescriptionSummary"))
            : prescriptionSummary));
        clinicalMapper.upsertLastVisitSnapshot(payload);
        return finalizeSnapshot(registerId, enrichSnapshot(registerId, lastVisitMapper.selectByRegisterId(registerId)));
    }

    public List<Map<String, Object>> suggestDrugs(Long registerId, String keyword) {
        List<Map<String, Object>> result = new ArrayList<>(clinicalMapper.selectPrescriptionsByRegisterId(registerId));
        if (keyword != null && !keyword.isBlank()) {
            for (Map<String, Object> drug : clinicalMapper.searchDrugs(keyword.trim(), 10)) {
                boolean exists = result.stream().anyMatch(r ->
                    drug.get("drugId") != null && drug.get("drugId").equals(r.get("drugId"))
                );
                if (!exists) {
                    result.add(drug);
                }
            }
        }
        if (result.isEmpty()) {
            result.addAll(clinicalMapper.searchDrugs(keyword != null ? keyword : "二甲双胍", 8));
        }
        return result;
    }

    public List<Map<String, Object>> suggestDiagnoses(Long registerId) {
        List<Map<String, Object>> rows = clinicalMapper.selectDiseasesByRegisterId(registerId);
        if (rows.isEmpty()) {
            Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
            if (medicalRecord != null && medicalRecord.get("diagnosis") != null) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("diseaseName", medicalRecord.get("diagnosis"));
                fallback.put("diagnosisText", medicalRecord.get("diagnosis"));
                fallback.put("treatmentDirection", medicalRecord.get("treatmentProposal"));
                fallback.put("source", "medical_record");
                rows = List.of(fallback);
            }
        }
        return rows;
    }

    private Map<String, Object> finalizeSnapshot(Long registerId, Map<String, Object> enriched) {
        if (!hasMeaningfulSnapshot(enriched)) {
            return emptySnapshot(registerId);
        }
        enriched.put("registerId", registerId);
        enriched.put("hasData", true);
        return enriched;
    }

    private Map<String, Object> emptySnapshot(Long registerId) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("registerId", registerId);
        empty.put("hasData", false);
        return empty;
    }

    private boolean hasMeaningfulSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        if (!isBlank(snapshot.get("diagnosisSummary"))) {
            return true;
        }
        if (!isBlank(snapshot.get("chiefComplaint"))) {
            return true;
        }
        if (!isBlank(snapshot.get("treatmentAdvice"))) {
            return true;
        }
        if (!parseJsonMap(snapshot.get("professionalMetrics")).isEmpty()) {
            return true;
        }
        List<Map<String, Object>> labItems = snapshot.get("labItems") instanceof List<?> list
            ? castLabItems(list)
            : List.of();
        if (!labItems.isEmpty()) {
            return true;
        }
        if (!parseJsonList(snapshot.get("labPanel")).isEmpty()) {
            return true;
        }
        return !parseJsonList(snapshot.get("prescriptionSummary")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castLabItems(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, val) -> row.put(String.valueOf(key), val));
                result.add(row);
            }
        }
        return result;
    }

    private Map<String, Object> enrichSnapshot(Long registerId, Map<String, Object> raw) {
        Map<String, Object> snapshot = new LinkedHashMap<>(raw);
        Integer departmentId = clinicalMapper.selectRegisterDepartmentId(registerId);
        List<Map<String, Object>> labItems = lastVisitMapper.selectLabItemsByRegisterId(registerId);

        Map<String, Object> metrics = parseJsonMap(snapshot.get("professionalMetrics"));
        if (metrics.isEmpty() && !labItems.isEmpty()) {
            metrics = buildMetricsFromLabItems(labItems);
        }
        if (metrics.isEmpty() && FollowUpDepartmentConstants.isEndocrine(departmentId)) {
            metrics = defaultDemoMetrics();
        }
        snapshot.put("professionalMetrics", metrics);

        List<Map<String, Object>> labPanel = parseJsonList(snapshot.get("labPanel"));
        if (labPanel.isEmpty() && !labItems.isEmpty()) {
            labPanel = buildLabPanelFromItems(labItems);
        }
        snapshot.put("labPanel", labPanel);
        snapshot.put("labItems", labItems);
        snapshot.put("prescriptionSummary", parseJsonList(snapshot.get("prescriptionSummary")));

        if (isBlank(snapshot.get("diagnosisSummary"))) {
            Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
            List<Map<String, Object>> diseases = outcomeMapper.selectPatientDiseases(registerId);
            String diagnosis = buildDiagnosisSummary(medicalRecord, diseases);
            if (!"待完善诊断摘要".equals(diagnosis)) {
                snapshot.put("diagnosisSummary", diagnosis);
            }
        }

        if (isBlank(snapshot.get("chiefComplaint"))) {
            Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
            if (medicalRecord != null) {
                Object complaint = medicalRecord.get("chiefComplaint");
                if (complaint == null) {
                    complaint = medicalRecord.get("readme");
                }
                if (complaint != null) {
                    snapshot.put("chiefComplaint", String.valueOf(complaint));
                }
            }
        }

        if (isBlank(snapshot.get("treatmentAdvice"))) {
            Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
            if (medicalRecord != null) {
                Object advice = medicalRecord.get("treatmentProposal");
                if (advice == null) {
                    advice = medicalRecord.get("proposal");
                }
                if (advice != null) {
                    snapshot.put("treatmentAdvice", String.valueOf(advice));
                }
            }
        }

        return snapshot;
    }

    private String resolveProfessionalMetricsJson(Map<String, Object> existing, Long registerId) {
        if (existing != null) {
            Map<String, Object> metrics = parseJsonMap(existing.get("professionalMetrics"));
            if (!metrics.isEmpty()) {
                return toJson(metrics);
            }
        }
        List<Map<String, Object>> labItems = lastVisitMapper.selectLabItemsByRegisterId(registerId);
        if (!labItems.isEmpty()) {
            return toJson(buildMetricsFromLabItems(labItems));
        }
        Integer departmentId = clinicalMapper.selectRegisterDepartmentId(registerId);
        if (FollowUpDepartmentConstants.isEndocrine(departmentId)) {
            return toJson(defaultDemoMetrics());
        }
        return toJson(Map.of());
    }

    private Map<String, Object> buildMetricsFromLabItems(List<Map<String, Object>> labItems) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map<String, Object> item : labItems) {
            String code = item.get("metricCode") != null
                ? String.valueOf(item.get("metricCode"))
                : String.valueOf(item.get("metric_code"));
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("value", item.get("metricValue") != null ? item.get("metricValue") : item.get("metric_value"));
            metric.put("unit", item.get("unit"));
            metric.put("label", item.get("label"));
            Object flag = item.get("abnormalFlag") != null ? item.get("abnormalFlag") : item.get("abnormal_flag");
            if (flag != null) {
                metric.put("abnormalFlag", String.valueOf(flag));
            }
            metrics.put(code, metric);
        }
        return metrics;
    }

    private List<Map<String, Object>> buildLabPanelFromItems(List<Map<String, Object>> labItems) {
        List<Map<String, Object>> panel = new ArrayList<>();
        for (Map<String, Object> item : labItems) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", item.get("metricCode") != null ? item.get("metricCode") : item.get("metric_code"));
            row.put("label", item.get("label"));
            row.put("value", item.get("metricValue") != null ? item.get("metricValue") : item.get("metric_value"));
            row.put("unit", item.get("unit"));
            row.put("refRange", item.get("refRange") != null ? item.get("refRange") : item.get("ref_range"));
            row.put("flag", item.get("abnormalFlag") != null ? item.get("abnormalFlag") : item.get("abnormal_flag"));
            panel.add(row);
        }
        return panel;
    }

    private Map<String, Object> defaultDemoMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("hba1c", metricEntry(7.8, "%", "糖化血红蛋白", "high"));
        metrics.put("fasting_glucose", metricEntry(8.2, "mmol/L", "空腹血糖", "high"));
        metrics.put("postprandial_glucose", metricEntry(11.5, "mmol/L", "餐后2h血糖", "high"));
        return metrics;
    }

    private Map<String, Object> metricEntry(Object value, String unit, String label, String flag) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("value", value);
        metric.put("unit", unit);
        metric.put("label", label);
        metric.put("abnormalFlag", flag);
        return metric;
    }

    private Map<String, Object> parseJsonMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        String text = extractJsonText(value);
        if (text.isBlank() || "{}".equals(text.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(text, MAP_TYPE);
        } catch (Exception ex) {
            log.debug("Failed to parse snapshot metrics json: {}", text);
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> parseJsonList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((key, val) -> row.put(String.valueOf(key), val));
                    result.add(row);
                }
            }
            return result;
        }
        String text = extractJsonText(value);
        if (text.isBlank() || "[]".equals(text.trim())) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(text, LIST_MAP_TYPE);
        } catch (Exception ex) {
            log.debug("Failed to parse snapshot list json: {}", text);
            return new ArrayList<>();
        }
    }

    private String extractJsonText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            Class<?> pgObjectClass = Class.forName("org.postgresql.util.PGobject");
            if (pgObjectClass.isInstance(value)) {
                Object jsonValue = pgObjectClass.getMethod("getValue").invoke(value);
                return jsonValue != null ? String.valueOf(jsonValue) : "";
            }
        } catch (Exception ignored) {
            // not PGobject
        }
        return String.valueOf(value);
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private String buildDiagnosisSummary(Map<String, Object> medicalRecord, List<Map<String, Object>> diseases) {
        if (medicalRecord != null && medicalRecord.get("diagnosis") != null) {
            return String.valueOf(medicalRecord.get("diagnosis"));
        }
        if (diseases != null && !diseases.isEmpty() && diseases.get(0).get("diseaseName") != null) {
            return String.valueOf(diseases.get(0).get("diseaseName"));
        }
        return "待完善诊断摘要";
    }

    private List<Map<String, Object>> buildPrescriptionSummary(List<Map<String, Object>> prescriptions) {
        List<Map<String, Object>> summary = new ArrayList<>();
        if (prescriptions == null) {
            return summary;
        }
        for (Map<String, Object> rx : prescriptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("drugId", rx.get("drugId"));
            item.put("drugName", rx.get("drugName"));
            item.put("drugFormat", rx.get("drugFormat"));
            item.put("drugUsage", rx.get("drugUsage"));
            item.put("drugNumber", rx.get("drugNumber"));
            summary.add(item);
        }
        return summary;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value != null ? value : List.of());
        } catch (Exception ex) {
            return "[]";
        }
    }
}
