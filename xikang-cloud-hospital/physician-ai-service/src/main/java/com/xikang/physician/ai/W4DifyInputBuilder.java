package com.xikang.physician.ai;

import com.xikang.physician.client.PhysicianClinicalClient;
import com.xikang.physician.mapper.PhysicianAiMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class W4DifyInputBuilder {

    private final PhysicianClinicalClient physicianClinicalClient;
    private final PhysicianAiMapper physicianAiMapper;

    public W4DifyInputBuilder(PhysicianClinicalClient physicianClinicalClient, PhysicianAiMapper physicianAiMapper) {
        this.physicianClinicalClient = physicianClinicalClient;
        this.physicianAiMapper = physicianAiMapper;
    }

    public Map<String, Object> build(Long registerId) {
        Map<String, Object> register = physicianClinicalClient.getRegister(registerId);
        Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
        Map<String, Object> consult = physicianClinicalClient.getLatestAiConsultation(registerId);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("register_id", String.valueOf(registerId));
        inputs.put("patient_info_text", buildPatientInfoText(register));
        inputs.put("chief_complaint", textOrEmpty(record, "readme"));
        inputs.put("present_illness", textOrEmpty(record, "present"));
        inputs.put("past_history", textOrEmpty(record, "history"));
        inputs.put("allergy_history", textOrEmpty(record, "allergy"));
        inputs.put("preliminary_diagnosis_text", textOrEmpty(record, "preliminaryDiagnosis"));
        inputs.put("preliminary_diseases_text", buildPreliminaryDiseasesText(record));
        inputs.put("check_results_text", buildCheckResultsText(registerId));
        inputs.put("inspection_results_text", buildInspectionResultsText(registerId));
        inputs.put("w3_analysis_text", buildW3AnalysisText(registerId));
        inputs.put("abnormal_indicators_text", buildAbnormalIndicatorsText(registerId));
        inputs.put("ai_previsit_summary", consult == null ? "" : textOrEmpty(consult, "aiSummary"));
        inputs.put("doctor_notes", textOrEmpty(record, "physique"));
        return inputs;
    }

    private static String buildPatientInfoText(Map<String, Object> register) {
        if (register == null || register.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String gender = textOrEmpty(register, "gender");
        if (!gender.isEmpty()) {
            parts.add(gender);
        }
        Object age = register.get("age");
        if (age != null && !String.valueOf(age).trim().isEmpty()) {
            parts.add(String.valueOf(age).trim() + "岁");
        }
        String dept = textOrEmpty(register, "deptName");
        if (!dept.isEmpty()) {
            parts.add(dept);
        }
        return String.join("，", parts);
    }

    private String buildPreliminaryDiseasesText(Map<String, Object> record) {
        if (record == null) {
            return "";
        }
        Long medicalRecordId = toLong(record.get("id"));
        if (medicalRecordId == null) {
            return "";
        }
        List<Map<String, Object>> diseases = physicianClinicalClient.getDiseasesByMedicalRecordId(medicalRecordId);
        if (diseases == null || diseases.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> disease : diseases) {
            String icd = textOrEmpty(disease, "diseaseIcd");
            String name = textOrEmpty(disease, "diseaseName");
            String category = textOrEmpty(disease, "diseaseCategory");
            if (icd.isEmpty() && name.isEmpty()) {
                continue;
            }
            lines.add(icd + "|" + name + "|" + category);
        }
        return String.join("\n", lines);
    }

    private String buildCheckResultsText(Long registerId) {
        return buildResultsText(physicianClinicalClient.getCheckResults(registerId), "checkResult");
    }

    private String buildInspectionResultsText(Long registerId) {
        return buildResultsText(physicianClinicalClient.getInspectionResults(registerId), "inspectionResult");
    }

    private static String buildResultsText(List<Map<String, Object>> rows, String resultKey) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object result = row.get(resultKey);
            if (result == null || String.valueOf(result).trim().isEmpty()) {
                continue;
            }
            String techName = textOrEmpty(row, "techName");
            String prefix = techName.isEmpty() ? "" : techName + "：";
            lines.add(prefix + String.valueOf(result).trim());
        }
        return String.join("\n", lines);
    }

    private String buildW3AnalysisText(Long registerId) {
        List<Map<String, Object>> analyses = physicianAiMapper.selectExamAnalysisByRegisterId(registerId);
        if (analyses == null || analyses.isEmpty()) {
            return "";
        }
        String clinicalImpression = "";
        String overallAnalysis = "";
        for (Map<String, Object> row : analyses) {
            Map<String, Object> indicators = parseAbnormalIndicators(row.get("abnormalIndicators"));
            Map<String, Object> w3Global = JsonMapUtils.asMap(indicators.get("w3Global"));
            if (clinicalImpression.isEmpty()) {
                clinicalImpression = String.valueOf(w3Global.getOrDefault("clinicalImpression", "")).trim();
            }
            if (overallAnalysis.isEmpty()) {
                overallAnalysis = String.valueOf(w3Global.getOrDefault("overallAnalysis", "")).trim();
            }
            if (overallAnalysis.isEmpty() && row.get("correlationAnalysis") != null) {
                overallAnalysis = String.valueOf(row.get("correlationAnalysis")).trim();
            }
        }
        List<String> parts = new ArrayList<>();
        if (!clinicalImpression.isEmpty()) {
            parts.add(clinicalImpression);
        }
        if (!overallAnalysis.isEmpty() && !overallAnalysis.equals(clinicalImpression)) {
            parts.add(overallAnalysis);
        }
        return String.join("\n", parts);
    }

    private String buildAbnormalIndicatorsText(Long registerId) {
        List<Map<String, Object>> analyses = physicianAiMapper.selectExamAnalysisByRegisterId(registerId);
        if (analyses == null || analyses.isEmpty()) {
            return "";
        }
        Set<String> abnormalItems = new LinkedHashSet<>();
        for (Map<String, Object> row : analyses) {
            Map<String, Object> indicators = parseAbnormalIndicators(row.get("abnormalIndicators"));
            for (Map<String, Object> indicatorRow : listOfMaps(indicators.get("indicatorRows"))) {
                String status = String.valueOf(indicatorRow.getOrDefault("status", "")).trim().toLowerCase();
                if (!isAbnormalStatus(status)) {
                    continue;
                }
                String itemName = textOrEmpty(indicatorRow, "itemName");
                if (itemName.isEmpty()) {
                    itemName = textOrEmpty(indicatorRow, "item_name");
                }
                if (itemName.isEmpty()) {
                    continue;
                }
                String value = textOrEmpty(indicatorRow, "value");
                String unit = textOrEmpty(indicatorRow, "unit");
                String note = itemName;
                if (!value.isEmpty()) {
                    note += " " + value;
                    if (!unit.isEmpty()) {
                        note += unit;
                    }
                }
                note += "↑";
                abnormalItems.add(note);
            }
            for (String finding : listOfStrings(indicators.get("keyFindings"))) {
                if (!finding.isBlank()) {
                    abnormalItems.add(finding.trim());
                }
            }
        }
        return String.join("；", abnormalItems);
    }

    private static Map<String, Object> parseAbnormalIndicators(Object raw) {
        Map<String, Object> parsed = JsonMapUtils.asMap(raw);
        if (parsed.containsKey("keyFindings") || parsed.containsKey("techName") || parsed.containsKey("indicatorRows")) {
            return parsed;
        }
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("techName", "");
        legacy.put("keyFindings", listOfStrings(raw));
        legacy.put("indicatorRows", List.of());
        return legacy;
    }

    private static boolean isAbnormalStatus(String status) {
        return "high".equals(status) || "low".equals(status) || "abnormal".equals(status)
            || "attention".equals(status) || "warning".equals(status) || "danger".equals(status);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static List<String> listOfStrings(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        out.add(text);
                    }
                }
            }
            return out;
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }

    private static String textOrEmpty(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }
}
