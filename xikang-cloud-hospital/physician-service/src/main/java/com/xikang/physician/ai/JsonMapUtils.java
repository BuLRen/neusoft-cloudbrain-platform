package com.xikang.physician.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

final class JsonMapUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonMapUtils() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try {
                    return MAPPER.readValue(trimmed, new TypeReference<>() {
                    });
                } catch (Exception ignored) {
                    return Map.of();
                }
            }
        }
        try {
            return MAPPER.convertValue(value, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
