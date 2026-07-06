package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.dto.SimulationConfigSaveRequest;
import com.xikang.medtech.entity.SimulationConfig;
import com.xikang.medtech.mapper.SimulationConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SimulationConfigAdminService {

    private static final String DEFAULT_OUTPUT_SCHEMA = """
        {"type":"object","required":["checkName","simulatedForDiseases","resultItems","conclusion","notice"]}
        """;

    private final SimulationConfigMapper simulationConfigMapper;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listAll(String keyword) {
        return simulationConfigMapper.selectAll(normalize(keyword)).stream()
            .map(this::toListItem)
            .toList();
    }

    public Optional<Map<String, Object>> getDetail(Integer id) {
        SimulationConfig config = simulationConfigMapper.selectById(id);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(toDetailPayload(config));
    }

    public Map<String, Object> create(SimulationConfigSaveRequest request) {
        validateSaveRequest(request);
        SimulationConfig existing = simulationConfigMapper.selectByConfigKey(normalize(request.getConfigKey()));
        if (existing != null) {
            throw new IllegalArgumentException("配置键已存在：" + request.getConfigKey());
        }

        SimulationConfig config = fromRequest(request, null);
        config.setVersion(1);
        simulationConfigMapper.insert(config);
        return toDetailPayload(simulationConfigMapper.selectById(config.getId()));
    }

    public Map<String, Object> update(Integer id, SimulationConfigSaveRequest request) {
        SimulationConfig current = simulationConfigMapper.selectById(id);
        if (current == null) {
            throw new IllegalArgumentException("配置不存在");
        }
        validateSaveRequest(request);

        SimulationConfig duplicate = simulationConfigMapper.selectByConfigKey(normalize(request.getConfigKey()));
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("配置键已被其他记录占用：" + request.getConfigKey());
        }

        SimulationConfig config = fromRequest(request, current);
        simulationConfigMapper.update(config);
        return toDetailPayload(simulationConfigMapper.selectById(id));
    }

    public void delete(Integer id) {
        SimulationConfig current = simulationConfigMapper.selectById(id);
        if (current == null) {
            throw new IllegalArgumentException("配置不存在");
        }
        simulationConfigMapper.deleteById(id);
    }

    private void validateSaveRequest(SimulationConfigSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (normalize(request.getConfigKey()).isEmpty()) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        if (normalize(request.getCheckName()).isEmpty()) {
            throw new IllegalArgumentException("检查名称不能为空");
        }
        if (request.getPromptSections() == null || request.getPromptSections().isEmpty()) {
            throw new IllegalArgumentException("提示词片段不能为空");
        }
        for (String key : List.of("role", "scope", "itemCatalog", "referenceRanges", "normalRules", "abnormalRules", "outputFormat")) {
            if (normalize(request.getPromptSections().get(key)).isEmpty()) {
                throw new IllegalArgumentException("提示词片段缺少必填项：" + key);
            }
        }
    }

    private SimulationConfig fromRequest(SimulationConfigSaveRequest request, SimulationConfig current) {
        SimulationConfig config = new SimulationConfig();
        if (current != null) {
            config.setId(current.getId());
            config.setVersion((current.getVersion() == null ? 1 : current.getVersion()) + 1);
        }
        config.setConfigKey(normalize(request.getConfigKey()));
        config.setTechCode(emptyToNull(normalize(request.getTechCode())));
        config.setCheckName(normalize(request.getCheckName()));
        config.setMatchKeywords(emptyToNull(normalize(request.getMatchKeywords())));
        config.setEnabled(request.getEnabled() == null || request.getEnabled());
        config.setSimulationMode(normalize(request.getSimulationMode()).isEmpty() ? "lab_items" : normalize(request.getSimulationMode()));
        config.setPromptSections(toJson(request.getPromptSections()));
        config.setDiseaseMappings(toJson(request.getDiseaseMappings() == null ? List.of() : request.getDiseaseMappings()));
        config.setOutputSchema(toJson(resolveOutputSchema(request.getOutputSchema())));
        config.setDefaults(toJson(resolveDefaults(request.getDefaults(), request.getCheckName())));
        return config;
    }

    private Map<String, Object> toListItem(SimulationConfig config) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", config.getId());
        item.put("configKey", config.getConfigKey());
        item.put("techCode", config.getTechCode());
        item.put("checkName", config.getCheckName());
        item.put("matchKeywords", config.getMatchKeywords());
        item.put("enabled", config.getEnabled() == null || config.getEnabled());
        item.put("simulationMode", config.getSimulationMode());
        item.put("version", config.getVersion());
        item.put("updatedAt", config.getUpdatedAt());
        return item;
    }

    private Map<String, Object> toDetailPayload(SimulationConfig config) {
        Map<String, Object> data = toListItem(config);
        data.put("promptSections", parseJsonObject(config.getPromptSections()));
        data.put("diseaseMappings", parseJsonValue(config.getDiseaseMappings()));
        data.put("outputSchema", parseJsonObject(config.getOutputSchema()));
        data.put("defaults", parseJsonObject(config.getDefaults()));
        data.put("createdAt", config.getCreatedAt());
        return data;
    }

    private Map<String, Object> resolveOutputSchema(Map<String, Object> outputSchema) {
        if (outputSchema != null && !outputSchema.isEmpty()) {
            return outputSchema;
        }
        return parseJsonObject(DEFAULT_OUTPUT_SCHEMA);
    }

    private Map<String, String> resolveDefaults(Map<String, String> defaults, String checkName) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (defaults != null) {
            resolved.putAll(defaults);
        }
        resolved.putIfAbsent("notice", "本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。");
        resolved.putIfAbsent("normalConclusion", checkName + "模拟结果未见明显异常");
        return resolved;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败");
        }
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

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
