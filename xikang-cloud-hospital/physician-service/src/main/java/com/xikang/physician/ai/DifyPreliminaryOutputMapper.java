package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Component
public class DifyPreliminaryOutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;

    public DifyPreliminaryOutputMapper(DifyAiProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> toPreliminaryResult(Map<String, Object> rawOutputs, List<Map<String, Object>> diseaseCatalog) {
        DifyAiProperties.PreliminaryOutputKeys keys = properties.getPreliminaryOutputKeys();
        Map<String, Object> source = expandSource(rawOutputs, keys);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("diagnosisText", str(firstNonBlank(source, keys.getDiagnosisText(), "diagnosisText", "diagnosis_text", "text")));
        out.put("diagnosisBasis", str(firstNonBlank(source, keys.getDiagnosisBasis(), "diagnosisBasis", "diagnosis_basis", "basis")));
        out.put("confidence", normalizeConfidence(firstPresent(source, keys.getConfidence(), "confidence", "confidence_score")));
        out.put("suggestedDiseases", resolveSuggestedDiseases(source, keys, diseaseCatalog));
        return out;
    }

    private Map<String, Object> expandSource(Map<String, Object> rawOutputs, DifyAiProperties.PreliminaryOutputKeys keys) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return Map.of();
        }
        if (!keys.isParseJsonFromText()) {
            return rawOutputs;
        }
        String textKey = keys.getDiagnosisText();
        if (textKey == null || textKey.isBlank()) {
            textKey = "text";
        }
        Object textVal = rawOutputs.get(textKey);
        if (textVal == null) {
            textVal = rawOutputs.get("text");
        }
        if (textVal instanceof String text && text.trim().startsWith("{")) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(text, new TypeReference<>() {
                });
                Map<String, Object> merged = new LinkedHashMap<>(rawOutputs);
                merged.putAll(parsed);
                return merged;
            } catch (Exception ignored) {
                return rawOutputs;
            }
        }
        return rawOutputs;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveSuggestedDiseases(
        Map<String, Object> source,
        DifyAiProperties.PreliminaryOutputKeys keys,
        List<Map<String, Object>> diseaseCatalog
    ) {
        Object raw = firstPresent(source, keys.getSuggestedDiseases(), "suggestedDiseases", "suggested_diseases", "diseases");
        List<Map<String, Object>> parsed = parseSuggestedList(raw);
        if (parsed.isEmpty() && source.get("text") instanceof String text && !text.isBlank()) {
            parsed = List.of(Map.of("diseaseName", text.trim()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : parsed) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("diseaseName", str(item.get("diseaseName") != null ? item.get("diseaseName") : item.get("name")));
            row.put("recommendIcd", str(item.get("recommendIcd") != null ? item.get("recommendIcd") : item.get("icd")));
            Object id = item.get("diseaseId") != null ? item.get("diseaseId") : item.get("id");
            if (id != null) {
                row.put("diseaseId", toLong(id));
            } else {
                matchDiseaseId(row, diseaseCatalog);
            }
            if (!str(row.get("diseaseName")).isBlank()) {
                result.add(row);
            }
        }
        return result;
    }

    private void matchDiseaseId(Map<String, Object> row, List<Map<String, Object>> catalog) {
        String name = str(row.get("diseaseName"));
        String icd = str(row.get("recommendIcd"));
        for (Map<String, Object> disease : catalog) {
            String diseaseName = str(disease.get("diseaseName"));
            String diseaseIcd = str(disease.get("diseaseIcd"));
            if (!icd.isBlank() && icd.equalsIgnoreCase(diseaseIcd)) {
                row.put("diseaseId", toLong(disease.get("id")));
                return;
            }
            if (!name.isBlank() && (diseaseName.contains(name) || name.contains(diseaseName))) {
                row.put("diseaseId", toLong(disease.get("id")));
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseSuggestedList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                } else if (item instanceof String text && !text.isBlank()) {
                    out.add(Map.of("diseaseName", text));
                }
            }
            return out;
        }
        if (raw instanceof String text) {
            if (text.trim().startsWith("[")) {
                try {
                    return MAPPER.readValue(text, new TypeReference<>() {
                    });
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            if (!text.isBlank()) {
                return List.of(Map.of("diseaseName", text));
            }
        }
        return List.of();
    }

    private static Object firstPresent(Map<String, Object> source, String configuredKey, String... fallbacks) {
        if (configuredKey != null && !configuredKey.isBlank() && source.containsKey(configuredKey)) {
            return source.get(configuredKey);
        }
        for (String key : fallbacks) {
            if (source.containsKey(key) && source.get(key) != null) {
                return source.get(key);
            }
        }
        return null;
    }

    private static String firstNonBlank(Map<String, Object> source, String configuredKey, String... fallbacks) {
        Object val = firstPresent(source, configuredKey, fallbacks);
        return str(val);
    }

    private static Double normalizeConfidence(Object value) {
        if (value == null) {
            return null;
        }
        double num;
        if (value instanceof Number number) {
            num = number.doubleValue();
        } else {
            try {
                num = Double.parseDouble(String.valueOf(value).replace("%", "").trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (num > 0 && num <= 1) {
            return num * 100;
        }
        return num;
    }

    private static String str(Object value) {
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
