package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Dify W2 workflow end-node outputs to the physician W2 API contract.
 */
@Component
public class DifyW2OutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;

    public DifyW2OutputMapper(DifyAiProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> toW2Result(Map<String, Object> rawOutputs) {
        Map<String, Object> source = unwrapSource(rawOutputs);
        if (source.isEmpty()) {
            return Map.of(
                "preliminaryAssessment", "",
                "recommendedExaminations", List.of(),
                "notRecommendedNote", "工作流未返回有效输出",
                "unmatchedSuggestions", List.of()
            );
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preliminaryAssessment", firstString(source, "preliminaryAssessment", "preliminary_assessment"));
        out.put("recommendedExaminations", firstList(source, "recommendedExaminations", "recommended_examinations"));
        out.put("notRecommendedNote", firstString(source, "notRecommendedNote", "not_recommended_note"));
        out.put("unmatchedSuggestions", firstList(source, "unmatchedSuggestions", "unmatched_suggestions"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapSource(Map<String, Object> rawOutputs) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return Map.of();
        }

        String rootKey = properties.getW2OutputKeys().getOutputRoot();
        if (rootKey != null && !rootKey.isBlank()) {
            Object node = rawOutputs.get(rootKey);
            Map<String, Object> parsed = asMap(node);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        for (String key : List.of("output_structured", "structured_output", "result")) {
            Map<String, Object> parsed = asMap(rawOutputs.get(key));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        Object text = rawOutputs.get("text");
        if (text instanceof String s && s.trim().startsWith("{")) {
            try {
                return MAPPER.readValue(s, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // fall through
            }
        }

        if (rawOutputs.containsKey("recommendedExaminations") || rawOutputs.containsKey("preliminaryAssessment")) {
            return rawOutputs;
        }

        return Map.of();
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> firstList(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
            }
        }
        return List.of();
    }
}
