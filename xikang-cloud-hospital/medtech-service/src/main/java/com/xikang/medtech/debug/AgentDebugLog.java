package com.xikang.medtech.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentDebugLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentDebugLog() {
    }

    public static void log(String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", "02d871");
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data);
            payload.put("timestamp", System.currentTimeMillis());
            Path path = Path.of(System.getProperty("user.dir")).resolve("../../debug-02d871.log").normalize();
            Files.writeString(
                path,
                MAPPER.writeValueAsString(payload) + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // debug-only
        }
    }
}
