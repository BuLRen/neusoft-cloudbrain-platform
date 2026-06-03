package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Dify workflow outputs to the physician preliminary-diagnosis API contract.
 * Supports {@code output_structured} with {@code answer} and {@code diseaseDetail}.
 */
@Component
public class DifyPreliminaryOutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;

    public DifyPreliminaryOutputMapper(DifyAiProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> toPreliminaryResult(Map<String, Object> rawOutputs) {
        DifyAiProperties.PreliminaryOutputKeys keys = properties.getPreliminaryOutputKeys();
        Map<String, Object> structured = unwrapOutputStructured(rawOutputs);
        Map<String, Object> source = !structured.isEmpty() ? structured : expandLegacySource(rawOutputs, keys);

        String answer = str(firstPresent(source, keys.getDiagnosisText(), "answer", "diagnosisText", "diagnosis_text", "text"));
        List<Map<String, Object>> suggested = mapDiseaseDetail(
            firstPresent(source, keys.getSuggestedDiseases(), "diseaseDetail", "disease_detail", "suggestedDiseases", "diseases")
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("diagnosisText", answer);
        out.put("diagnosisBasis", buildDiagnosisBasis(source, suggested));
        out.put("confidence", resolveOverallConfidence(source, suggested, keys));
        out.put("suggestedDiseases", suggested);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapOutputStructured(Map<String, Object> rawOutputs) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return Map.of();
        }
        String rootKey = properties.getPreliminaryOutputKeys().getOutputStructured();
        if (rootKey == null || rootKey.isBlank()) {
            rootKey = "output_structured";
        }
        Object node = rawOutputs.get(rootKey);
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

    private Map<String, Object> expandLegacySource(Map<String, Object> rawOutputs, DifyAiProperties.PreliminaryOutputKeys keys) {
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
    private List<Map<String, Object>> mapDiseaseDetail(Object raw) {
        List<Map<String, Object>> parsed = parseList(raw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : parsed) {
            String name = str(item.get("name") != null ? item.get("name") : item.get("diseaseName"));
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("diseaseName", name);
            String sym = str(item.get("sym"));
            if (!sym.isBlank()) {
                row.put("symptoms", sym);
            }
            String confidence = str(item.get("confidence"));
            if (!confidence.isBlank()) {
                row.put("confidenceLevel", confidence);
            }
            result.add(row);
        }
        return result;
    }

    private static String buildDiagnosisBasis(Map<String, Object> source, List<Map<String, Object>> suggested) {
        List<String> parts = new ArrayList<>();
        String recalled = str(source.get("isRecalled"));
        if (!recalled.isBlank()) {
            parts.add("知识库召回：" + recalled);
        }
        for (Map<String, Object> disease : suggested) {
            String sym = str(disease.get("symptoms"));
            if (!sym.isBlank()) {
                parts.add(str(disease.get("diseaseName")) + " — " + sym);
            }
        }
        return String.join("\n", parts);
    }

    private static Double resolveOverallConfidence(
        Map<String, Object> source,
        List<Map<String, Object>> suggested,
        DifyAiProperties.PreliminaryOutputKeys keys
    ) {
        Object configured = firstPresent(source, keys.getConfidence(), "confidence", "confidence_score");
        Double numeric = normalizeConfidence(configured);
        if (numeric != null) {
            return numeric;
        }
        if (!suggested.isEmpty()) {
            return normalizeConfidence(suggested.get(0).get("confidenceLevel"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
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

    private static Double normalizeConfidence(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            double num = number.doubleValue();
            return num > 0 && num <= 1 ? num * 100 : num;
        }
        String text = String.valueOf(value).trim();
        return switch (text) {
            case "高" -> 85.0;
            case "中" -> 60.0;
            case "低" -> 35.0;
            default -> {
                try {
                    double num = Double.parseDouble(text.replace("%", ""));
                    yield num > 0 && num <= 1 ? num * 100 : num;
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
