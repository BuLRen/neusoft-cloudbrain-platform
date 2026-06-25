package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Dify W3 workflow end-node outputs to the physician W3 API contract.
 */
@Component
public class DifyW3OutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> toW3Result(Map<String, Object> rawOutputs) {
        Map<String, Object> source = unwrapSource(rawOutputs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("examSummaries", firstExamSummaries(source));
        out.put("overallAnalysis", firstString(source, "overallAnalysis", "overall_analysis"));
        out.put("explicitNonDiagnosis", true);
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

        if (rawOutputs.containsKey("examSummaries") || rawOutputs.containsKey("overallAnalysis")) {
            return rawOutputs;
        }

        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> firstExamSummaries(Map<String, Object> source) {
        Object raw = source.get("examSummaries");
        if (raw == null) {
            raw = source.get("exam_summaries");
        }
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    summaries.add((Map<String, Object>) map);
                }
            }
            return summaries;
        }
        if (raw instanceof String text && !text.isBlank()) {
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
}
