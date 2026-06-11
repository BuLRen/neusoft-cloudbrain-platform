package com.xikang.medtech.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckSimulationOutputMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;

    public CheckSimulationOutputMapper(DifyAiProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractStructuredOutput(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return Map.of();
        }

        String structuredKey = properties.getCheckSimulateOutputKeys().getStructuredOutput();
        Object structured = outputs.get(structuredKey);
        Map<String, Object> parsed = parseStructuredObject(structured);
        if (isValidStructuredOutput(parsed)) {
            return parsed;
        }

        DifyAiProperties.CheckSimulateOutputKeys keys = properties.getCheckSimulateOutputKeys();
        for (String key : new String[] { "text", keys.getResultText(), "resultText" }) {
            Object text = outputs.get(key);
            if (!(text instanceof String textStr) || !textStr.trim().startsWith("{")) {
                continue;
            }
            try {
                Map<String, Object> fromText = MAPPER.readValue(textStr, new TypeReference<>() {});
                Map<String, Object> fromTextParsed = parseStructuredObject(fromText);
                if (isValidStructuredOutput(fromTextParsed)) {
                    return fromTextParsed;
                }
            } catch (Exception ignored) {
                // try next key
            }
        }

        if (isValidStructuredOutput(outputs)) {
            return outputs;
        }

        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStructuredObject(Object structured) {
        if (structured instanceof Map<?, ?> map) {
            Map<String, Object> result = (Map<String, Object>) map;
            Object nested = result.get("structured_output");
            if (nested == null) {
                nested = result.get("structuredOutput");
            }
            if (nested != null && nested != structured) {
                Map<String, Object> inner = parseStructuredObject(nested);
                if (isValidStructuredOutput(inner)) {
                    return inner;
                }
            }
            for (String wrapKey : new String[] { "value", "data", "content", "output" }) {
                Object wrapped = result.get(wrapKey);
                if (wrapped == null) {
                    continue;
                }
                Map<String, Object> inner = parseStructuredObject(wrapped);
                if (isValidStructuredOutput(inner)) {
                    return inner;
                }
            }
            if (isValidStructuredOutput(result)) {
                return result;
            }
            return Map.of();
        }
        if (structured instanceof String text && text.trim().startsWith("{")) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(text, new TypeReference<>() {});
                return isValidStructuredOutput(parsed) ? parsed : Map.of();
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    public boolean hasUsableStructuredOutput(Map<String, Object> outputs) {
        return isValidStructuredOutput(extractStructuredOutput(outputs));
    }

    private static boolean isValidStructuredOutput(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        Object resultItems = map.get("resultItems");
        if (resultItems instanceof List<?> list && !list.isEmpty()) {
            return true;
        }
        String checkName = asString(map.get("checkName"));
        if (checkName != null) {
            return true;
        }
        String conclusion = asString(map.get("conclusion"));
        return conclusion != null;
    }

    public Map<String, Object> mapStructuredToFormValues(Map<String, Object> structured, List<Map<String, Object>> schemaFields) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (structured == null || structured.isEmpty()) {
            return values;
        }

        String conclusion = asString(structured.get("conclusion"));
        String notice = asString(structured.get("notice"));

        String primaryKey = findFieldKey(schemaFields, "checkResult");
        if (primaryKey == null) {
            primaryKey = findFieldKey(schemaFields, "inspectionResult");
        }
        if (primaryKey == null) {
            primaryKey = firstTextareaFieldKey(schemaFields);
        }

        if (conclusion != null && primaryKey != null) {
            values.put(primaryKey, conclusion);
        }

        String remarkKey = findFieldKey(schemaFields, "checkRemark");
        if (remarkKey == null) {
            remarkKey = findFieldKey(schemaFields, "inspectionRemark");
        }
        if (notice != null && remarkKey != null) {
            values.put(remarkKey, notice);
        }

        return values;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> mapToFormValues(Map<String, Object> outputs, List<Map<String, Object>> schemaFields) {
        Map<String, Object> structured = extractStructuredOutput(outputs);
        if (!structured.isEmpty()) {
            return mapStructuredToFormValues(structured, schemaFields);
        }

        if (outputs == null || outputs.isEmpty()) {
            return Map.of();
        }

        DifyAiProperties.CheckSimulateOutputKeys keys = properties.getCheckSimulateOutputKeys();
        Map<String, Object> values = new LinkedHashMap<>();

        Object valuesRoot = outputs.get(keys.getValuesRoot());
        if (valuesRoot instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        if (values.isEmpty()) {
            String resultText = firstNonBlank(
                asString(outputs.get(keys.getResultText())),
                asString(outputs.get("resultText")),
                asString(outputs.get("text"))
            );
            if (resultText != null) {
                String targetKey = firstTextareaFieldKey(schemaFields);
                if (targetKey != null) {
                    values.put(targetKey, resultText);
                }
            }
        }

        return values;
    }

    public String extractResultText(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }

        Map<String, Object> structured = extractStructuredOutput(outputs);
        if (!structured.isEmpty()) {
            return asString(structured.get("conclusion"));
        }

        DifyAiProperties.CheckSimulateOutputKeys keys = properties.getCheckSimulateOutputKeys();
        return firstNonBlank(
            asString(outputs.get(keys.getResultText())),
            asString(outputs.get("resultText")),
            asString(outputs.get("text"))
        );
    }

    public Map<String, Object> mapCtInferenceToFormValues(Map<String, Object> ctResult) {
        Map<String, Object> values = new LinkedHashMap<>();
        String findings = asString(ctResult.get("resultText"));
        String impression = asString(ctResult.get("aiImpression"));
        if (findings != null) {
            values.put("findings", findings);
        }
        if (impression != null) {
            values.put("impression", impression);
            values.put("conclusion", impression);
        }
        return values;
    }

    private static String findFieldKey(List<Map<String, Object>> schemaFields, String fieldKey) {
        for (Map<String, Object> field : schemaFields) {
            if (fieldKey.equals(String.valueOf(field.get("fieldKey")))) {
                return fieldKey;
            }
        }
        return null;
    }

    private static String firstTextareaFieldKey(List<Map<String, Object>> schemaFields) {
        for (Map<String, Object> field : schemaFields) {
            if ("textarea".equals(String.valueOf(field.get("fieldType")))) {
                return String.valueOf(field.get("fieldKey"));
            }
        }
        if (!schemaFields.isEmpty()) {
            return String.valueOf(schemaFields.get(0).get("fieldKey"));
        }
        return "checkResult";
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
