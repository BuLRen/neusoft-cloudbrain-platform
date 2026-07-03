package com.xikang.physician.simulation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.physician.simulation.entity.SimulationConfig;
import com.xikang.physician.simulation.mapper.SimulationConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SimulationConfigService {

    private final SimulationConfigMapper simulationConfigMapper;
    private final ObjectMapper objectMapper;

    public Optional<Map<String, Object>> resolveForWorkflow(String techCode, String checkName) {
        String normalizedTechCode = normalize(techCode);
        String normalizedCheckName = normalize(checkName);
        if (normalizedTechCode.isEmpty() && normalizedCheckName.isEmpty()) {
            return Optional.empty();
        }

        SimulationConfig config = simulationConfigMapper.selectBestMatch(normalizedTechCode, normalizedCheckName);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(toWorkflowPayload(config));
    }

    private Map<String, Object> toWorkflowPayload(SimulationConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configKey", config.getConfigKey());
        data.put("techCode", config.getTechCode());
        data.put("checkName", config.getCheckName());
        data.put("enabled", config.getEnabled() == null || config.getEnabled());
        data.put("simulationMode", config.getSimulationMode());
        data.put("version", config.getVersion());
        data.put("promptSections", parseJsonObject(config.getPromptSections()));
        data.put("diseaseMappings", parseJsonValue(config.getDiseaseMappings()));
        data.put("outputSchema", parseJsonObject(config.getOutputSchema()));
        data.put("defaults", parseJsonObject(config.getDefaults()));
        return data;
    }

    private Object parseJsonValue(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        Object parsed = parseJsonValue(json);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return Map.of();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
