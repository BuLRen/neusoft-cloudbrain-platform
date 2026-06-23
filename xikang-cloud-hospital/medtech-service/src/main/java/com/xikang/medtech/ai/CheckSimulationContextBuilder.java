package com.xikang.medtech.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
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
            appendPart(parts, "初步诊断", record.get("preliminaryDiagnosis"));
            appendPart(parts, "诊断", record.get("diagnosis"));
        }

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
            String preliminary = trimToNull(record.get("preliminaryDiagnosis"));
            if (preliminary != null) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", preliminary);
                row.put("diseaseSymptoms", "");
                diseases.add(row);
            }
        }

        List<Map<String, Object>> linked = simulationContextMapper.selectDiseasesByRegisterId(registerId);
        for (Map<String, Object> item : linked) {
            String name = trimToNull(item.get("diseaseName"));
            if (name == null) {
                continue;
            }
            boolean exists = diseases.stream().anyMatch(d -> name.equals(d.get("name")));
            if (!exists) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("diseaseSymptoms", "");
                diseases.add(row);
            }
        }

        return diseases;
    }

    public String serializePossibleDiseases(Long registerId) {
        try {
            return objectMapper.writeValueAsString(buildPossibleDiseases(registerId));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static void appendPart(List<String> parts, String label, Object value) {
        String text = trimToNull(value);
        if (text != null) {
            parts.add(label + "：" + text);
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
