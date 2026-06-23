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
        return MAPPER.convertValue(value, new TypeReference<>() {
        });
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
