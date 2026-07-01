package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Dify W4 workflow end-node outputs to the physician W4 API contract.
 */
@Component
public class DifyW4OutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> toW4Result(Map<String, Object> rawOutputs) {
        Map<String, Object> source = unwrapSource(rawOutputs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", firstString(source, "status"));
        out.put("registerId", firstLong(source, "registerId", "register_id"));
        out.put("suggestions", mapSuggestions(source.get("suggestions")));
        out.put("fallbackSuggestions", mapFallbackSuggestions(source.get("fallbackSuggestions"), source.get("fallback_suggestions")));
        out.put("clinicalSummaryForDoctor", firstString(source, "clinicalSummaryForDoctor", "clinical_summary_for_doctor"));
        out.put("differentialDiagnosis", mapDifferentialDiagnosis(source));
        out.put("warningSigns", firstStringList(source, "warningSigns", "warning_signs"));
        out.put("searchAdvice", firstString(source, "searchAdvice", "search_advice"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapSource(Map<String, Object> rawOutputs) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return Map.of();
        }

        for (String key : List.of("output_structured", "structured_output", "result")) {
            Map<String, Object> parsed = asMap(rawOutputs.get(key));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        Object text = rawOutputs.get("text");
        if (text instanceof String s && s.trim().startsWith("{")) {
            Map<String, Object> parsed = asMap(s);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        if (rawOutputs.containsKey("status") || rawOutputs.containsKey("suggestions")
            || rawOutputs.containsKey("fallbackSuggestions")) {
            return rawOutputs;
        }

        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapSuggestions(Object raw) {
        List<Map<String, Object>> sourceList = asListOfMaps(raw);
        List<Map<String, Object>> mapped = new ArrayList<>();
        int order = 1;
        for (Map<String, Object> item : sourceList) {
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("diseaseId", firstLong(item, "diseaseId", "disease_id"));
            suggestion.put("diagnosisName", firstString(item, "diagnosisName", "diagnosis_name", "diseaseName", "disease_name"));
            suggestion.put("recommendIcd", firstString(item, "recommendIcd", "recommend_icd", "icd"));
            suggestion.put("probability", normalizeProbability(item.get("probability")));
            suggestion.put("riskLevel", firstString(item, "riskLevel", "risk_level"));
            suggestion.put("treatmentDirection", firstString(item, "treatmentDirection", "treatment_direction"));
            suggestion.put("diagnosisBasis", firstString(item, "diagnosisBasis", "diagnosis_basis"));
            Integer sortOrder = firstInteger(item, "sortOrder", "sort_order");
            suggestion.put("sortOrder", sortOrder == null ? order : sortOrder);
            mapped.add(suggestion);
            order++;
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapFallbackSuggestions(Object primary, Object alternate) {
        Object raw = primary != null ? primary : alternate;
        List<Map<String, Object>> sourceList = asListOfMaps(raw);
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> item : sourceList) {
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("diagnosisName", firstString(item, "diagnosisName", "diagnosis_name", "diseaseName", "disease_name"));
            suggestion.put("estimatedIcdPrefix", firstString(item, "estimatedIcdPrefix", "estimated_icd_prefix", "recommendIcd", "recommend_icd"));
            suggestion.put("probability", normalizeProbability(item.get("probability")));
            suggestion.put("riskLevel", firstString(item, "riskLevel", "risk_level"));
            suggestion.put("diagnosisBasis", firstString(item, "diagnosisBasis", "diagnosis_basis"));
            suggestion.put("note", firstString(item, "note"));
            mapped.add(suggestion);
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapDifferentialDiagnosis(Map<String, Object> source) {
        Object raw = source.get("differentialDiagnosis");
        if (raw == null) {
            raw = source.get("differential_diagnosis");
        }
        List<Map<String, Object>> sourceList = asListOfMaps(raw);
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> item : sourceList) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("diagnosisName", firstString(item, "diagnosisName", "diagnosis_name", "diseaseName", "disease_name"));
            entry.put("reason", firstString(item, "reason", "diagnosisBasis", "diagnosis_basis"));
            mapped.add(entry);
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                } else if (item instanceof String text && text.trim().startsWith("{")) {
                    Map<String, Object> parsed = asMap(text);
                    if (!parsed.isEmpty()) {
                        result.add(parsed);
                    }
                }
            }
            return result;
        }
        if (raw instanceof String text && text.trim().startsWith("[")) {
            try {
                List<Map<String, Object>> parsed = MAPPER.readValue(text, new TypeReference<>() {
                });
                return parsed == null ? List.of() : parsed;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        if (node instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (node instanceof String text && text.trim().startsWith("{")) {
            try {
                return MAPPER.readValue(text, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static Long firstLong(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

    private static Integer firstInteger(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

    private static List<String> firstStringList(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).trim().isEmpty()) {
                        out.add(String.valueOf(item).trim());
                    }
                }
                return out;
            }
            if (value instanceof String text && text.trim().startsWith("[")) {
                try {
                    List<String> parsed = MAPPER.readValue(text, new TypeReference<>() {
                    });
                    return parsed == null ? List.of() : parsed;
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            if (value instanceof String text && !text.isBlank()) {
                return List.of(text.trim());
            }
        }
        return List.of();
    }

    /**
     * Dify may return 0~1 decimal; DB/fallback use 0~100 percent scale.
     */
    private static Double normalizeProbability(Object raw) {
        if (raw == null) {
            return null;
        }
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            try {
                value = Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (value > 0 && value <= 1) {
            return Math.round(value * 1000.0) / 10.0;
        }
        return value;
    }
}
