package com.xikang.medtech.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.mapper.SimulationContextMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckSimulationContextBuilder {

    private static final String PRELIMINARY_SOURCE_TYPE = "preliminary_diagnosis";

    private final SimulationContextMapper simulationContextMapper;
    private final ObjectMapper objectMapper;

    public String buildPatientContext(Long registerId) {
        List<String> parts = new ArrayList<>();

        Map<String, Object> record = simulationContextMapper.selectMedicalRecordByRegisterId(registerId);
        if (record != null) {
            appendPart(parts, "主诉", record.get("readme"));
            appendPart(parts, "现病史", record.get("present"));
            appendPart(parts, "既往史", record.get("history"));
            appendPart(parts, "过敏史", record.get("allergy"));
            appendPart(parts, "体格检查", record.get("physique"));
            appendPart(parts, "初步诊断", record.get("preliminaryDiagnosis"));
            appendPart(parts, "诊断", record.get("diagnosis"));
        }

        Map<String, Object> preliminaryMeta = loadPreliminaryAiMeta(registerId);
        appendPart(parts, "AI临床摘要", preliminaryMeta.get("clinicalSummary"));
        appendPart(parts, "AI主要诊断", preliminaryMeta.get("primaryDiagnosis"));

        Map<String, Object> consult = simulationContextMapper.selectLatestAiConsultationByRegisterId(registerId);
        if (consult != null) {
            appendPart(parts, "预问诊主诉", consult.get("chiefComplaint"));
            appendPart(parts, "症状时长", consult.get("symptomDuration"));
            appendPart(parts, "AI摘要", consult.get("aiSummary"));
        }

        return parts.isEmpty() ? "" : String.join("；", parts);
    }

    public List<Map<String, String>> buildPossibleDiseases(Long registerId) {
        List<Map<String, String>> diseases = new ArrayList<>();

        Map<String, Object> record = simulationContextMapper.selectMedicalRecordByRegisterId(registerId);
        if (record != null) {
            addDiseaseIfAbsent(diseases, trimToNull(record.get("preliminaryDiagnosis")), "");
        }

        for (Map<String, Object> item : simulationContextMapper.selectDiseasesByRegisterId(registerId)) {
            addDiseaseIfAbsent(diseases, trimToNull(item.get("diseaseName")), "");
        }

        Map<String, Object> preliminaryMeta = loadPreliminaryAiMeta(registerId);
        appendDiseasesFromMeta(diseases, preliminaryMeta.get("diseaseDetail"));
        appendDiseasesFromMeta(diseases, preliminaryMeta.get("suggestedDiseases"));

        return diseases;
    }

    public String serializePossibleDiseases(Long registerId) {
        try {
            return objectMapper.writeValueAsString(buildPossibleDiseases(registerId));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Map<String, Object> loadPreliminaryAiMeta(Long registerId) {
        Map<String, Object> log = simulationContextMapper.selectLatestAiMedicalRecordLogBySourceType(
            registerId,
            PRELIMINARY_SOURCE_TYPE
        );
        if (log == null) {
            return Map.of();
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        appendPartValue(meta, "primaryDiagnosis", log.get("aiDiagnosis"));

        Object raw = log.get("doctorModification");
        if (raw instanceof String text && !text.isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<>() {});
                meta.putAll(parsed);
            } catch (Exception ignored) {
                // ignore malformed meta
            }
        }
        return meta;
    }

    @SuppressWarnings("unchecked")
    private static void appendDiseasesFromMeta(List<Map<String, String>> diseases, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String name = firstNonBlank(
                    trimToNull(map.get("diseaseName")),
                    trimToNull(map.get("name")),
                    trimToNull(map.get("primaryDiagnosis"))
                );
                String symptoms = firstNonBlank(
                    trimToNull(map.get("diseaseSymptoms")),
                    trimToNull(map.get("symptoms")),
                    trimToNull(map.get("basis")),
                    ""
                );
                addDiseaseIfAbsent(diseases, name, symptoms == null ? "" : symptoms);
            } else {
                addDiseaseIfAbsent(diseases, trimToNull(item), "");
            }
        }
    }

    private static void addDiseaseIfAbsent(List<Map<String, String>> diseases, String name, String symptoms) {
        if (name == null) {
            return;
        }
        boolean exists = diseases.stream().anyMatch(d -> name.equals(d.get("name")));
        if (!exists) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("diseaseSymptoms", symptoms == null ? "" : symptoms);
            diseases.add(row);
        }
    }

    private static void appendPart(List<String> parts, String label, Object value) {
        String text = trimToNull(value);
        if (text != null) {
            parts.add(label + "：" + text);
        }
    }

    private static void appendPartValue(Map<String, Object> target, String key, Object value) {
        String text = trimToNull(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
