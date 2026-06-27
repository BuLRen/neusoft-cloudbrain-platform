package com.xikang.medtech.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CaseSummaryOutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> mapCaseSummaryOutputs(Map<String, Object> outputs) {
        Map<String, Object> root = unwrapStructured(outputs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseSummary", text(root, "caseSummary", "case_summary", "summary"));
        result.put("medicalAdvice", text(root, "medicalAdvice", "medical_advice", "advice"));
        result.put("riskAlerts", parseAlerts(root));
        result.put("followUpFocus", parseStringList(root, "followUpFocus", "follow_up_focus"));
        result.put("confidence", number(root, "confidence"));
        return result;
    }

    public Map<String, Object> mapMedicalChatOutputs(Map<String, Object> outputs) {
        Map<String, Object> root = unwrapStructured(outputs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", text(root, "reply", "answer", "text", "result"));
        result.put("refused", bool(root, "refused", "isRefused"));
        result.put("refusalReason", text(root, "refusalReason", "refusal_reason"));
        result.put("confidence", number(root, "confidence"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapStructured(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return Map.of();
        }
        for (String key : new String[] { "structured_output", "structuredOutput", "output_structured" }) {
            Object nested = outputs.get(key);
            if (nested instanceof Map<?, ?> map && !map.isEmpty()) {
                return (Map<String, Object>) map;
            }
            if (nested instanceof String text && text.trim().startsWith("{")) {
                try {
                    return MAPPER.readValue(text, Map.class);
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        Object text = outputs.get("text");
        if (text instanceof String str && str.trim().startsWith("{")) {
            try {
                return MAPPER.readValue(str, Map.class);
            } catch (Exception ignored) {
                return outputs;
            }
        }
        return outputs;
    }

    private String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private Double number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean bool(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value != null) {
                return Boolean.parseBoolean(String.valueOf(value));
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAlerts(Map<String, Object> map) {
        Object value = map.get("riskAlerts");
        if (value == null) {
            value = map.get("risk_alerts");
        }
        return parseStringListValue(value);
    }

    private List<String> parseStringList(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            List<String> list = parseStringListValue(map.get(key));
            if (!list.isEmpty()) {
                return list;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringListValue(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                try {
                    return parseStringListValue(MAPPER.readValue(trimmed, List.class));
                } catch (Exception ignored) {
                    return List.of(trimmed);
                }
            }
            return List.of(trimmed);
        }
        return List.of(String.valueOf(value));
    }
}
