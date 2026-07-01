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
public class W5DifyInputBuilder {

    private final PhysicianClinicalClient physicianClinicalClient;
    private final PhysicianAiMapper physicianAiMapper;

    public W5DifyInputBuilder(PhysicianClinicalClient physicianClinicalClient, PhysicianAiMapper physicianAiMapper) {
        this.physicianClinicalClient = physicianClinicalClient;
        this.physicianAiMapper = physicianAiMapper;
    }

    public Map<String, Object> build(Long registerId) {
        Map<String, Object> register = physicianClinicalClient.getRegister(registerId);
        Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("register_id", String.valueOf(registerId));
        inputs.put("patient_info_text", buildPatientInfoText(register));
        inputs.put("confirmed_diagnosis_text", record == null ? "" : textOrEmpty(record, "diagnosis"));
        inputs.put("w4_suggestions_text", buildW4SuggestionsText(registerId));
        inputs.put("allergy_history", record == null ? "" : textOrEmpty(record, "allergy"));
        inputs.put("past_history", record == null ? "" : textOrEmpty(record, "history"));
        inputs.put("chief_complaint", record == null ? "" : textOrEmpty(record, "readme"));
        inputs.put("w3_analysis_text", buildW3AnalysisText(registerId));
        inputs.put("abnormal_indicators_text", buildAbnormalIndicatorsText(registerId));
        inputs.put("preliminary_diagnosis_text", record == null ? "" : textOrEmpty(record, "preliminaryDiagnosis"));
        inputs.put("doctor_notes", record == null ? "" : textOrEmpty(record, "physique"));
        return inputs;
    }

    private String buildW4SuggestionsText(Long registerId) {
        List<Map<String, Object>> suggestions = physicianAiMapper.selectDiagnosisSuggestions(registerId);
        if (suggestions == null || suggestions.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : suggestions) {
            String icd = textOrEmpty(item, "recommendIcd");
            String name = textOrEmpty(item, "diseaseName");
            Object prob = item.get("probability");
            String treatment = textOrEmpty(item, "treatmentDirection");
            if (name.isEmpty()) {
                continue;
            }
            String probText = prob == null ? "" : String.valueOf(prob);
            lines.add(icd + "|" + name + "|" + probText + "|" + treatment);
        }
        return String.join("\n", lines);
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
        legacy.put("keyFindings", List.of());
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

    private static String textOrEmpty(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
