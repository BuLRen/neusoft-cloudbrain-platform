package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import com.xikang.medtech.mapper.HealthObservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthObservationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HealthObservationMapper healthObservationMapper;
    private final FollowUpOutcomeMapper followUpOutcomeMapper;
    private final FollowUpProperties followUpProperties;

    public List<Map<String, Object>> getMetrics(
        Long registerId,
        LocalDate from,
        LocalDate to,
        List<String> metricKeys
    ) {
        return getMetrics(registerId, from, to, metricKeys, null, null);
    }

    public List<Map<String, Object>> getMetrics(
        Long registerId,
        LocalDate from,
        LocalDate to,
        List<String> metricKeys,
        String sourceType,
        List<String> sourceTypes
    ) {
        List<Map<String, Object>> production = loadProductionMetrics(
            registerId, from, to, metricKeys, sourceType, sourceTypes
        );
        if (!production.isEmpty() || followUpProperties.isProductionMode()) {
            if (production.isEmpty() && followUpProperties.preferProductionObservations()
                && sourceType == null && (sourceTypes == null || sourceTypes.isEmpty())) {
                syncFromLabResults(registerId);
                production = loadProductionMetrics(registerId, from, to, metricKeys, sourceType, sourceTypes);
            }
            return production;
        }

        if (followUpProperties.isHybridMode()) {
            if (production.isEmpty() && sourceType == null && (sourceTypes == null || sourceTypes.isEmpty())) {
                syncFromLabResults(registerId);
                production = loadProductionMetrics(registerId, from, to, metricKeys, sourceType, sourceTypes);
            }
            if (!production.isEmpty()) {
                return production;
            }
            return followUpOutcomeMapper.selectHealthMetrics(registerId, from, to, metricKeys);
        }

        return followUpOutcomeMapper.selectHealthMetrics(registerId, from, to, metricKeys);
    }

    public List<Map<String, Object>> getRecentMetrics(Long registerId, LocalDate from, LocalDate to, int limit) {
        List<Map<String, Object>> metrics = getMetrics(registerId, from, to, null);
        if (metrics.size() <= limit) {
            return metrics;
        }
        List<Map<String, Object>> recent = new ArrayList<>(metrics.subList(Math.max(0, metrics.size() - limit), metrics.size()));
        recent.sort((a, b) -> String.valueOf(b.get("recordDate")).compareTo(String.valueOf(a.get("recordDate"))));
        return recent;
    }

    private List<Map<String, Object>> loadProductionMetrics(
        Long registerId,
        LocalDate from,
        LocalDate to,
        List<String> metricKeys,
        String sourceType,
        List<String> sourceTypes
    ) {
        if (!followUpProperties.preferProductionObservations()) {
            return List.of();
        }
        try {
            return healthObservationMapper.selectObservations(
                registerId, from, to, metricKeys, sourceType, sourceTypes
            );
        } catch (DataAccessException ex) {
            log.warn("patient_health_observation 读取失败（可能尚未执行 migrate_020）: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> insertPatientReportObservation(
        Long registerId,
        LocalDateTime observedAt,
        double metricValue,
        String note
    ) {
        LocalDateTime at = observedAt != null ? observedAt : LocalDateTime.now();
        healthObservationMapper.insertObservation(
            registerId,
            at,
            "blood_glucose",
            metricValue,
            "mmol/L",
            "patient_report",
            null,
            note
        );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("registerId", registerId);
        row.put("recordDate", at.toLocalDate().toString());
        row.put("recordedAt", at.toString());
        row.put("metricKey", "blood_glucose");
        row.put("metricValue", metricValue);
        row.put("unit", "mmol/L");
        row.put("source", "patient_report");
        row.put("note", note);
        return row;
    }

    public int countRecentGlucoseReports(Long registerId, int hours) {
        LocalDate from = LocalDate.now().minusDays(Math.max(1, hours / 24 + 1));
        List<Map<String, Object>> rows = getMetrics(
            registerId,
            from,
            null,
            List.of("blood_glucose"),
            "patient_report",
            null
        );
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        int count = 0;
        for (Map<String, Object> row : rows) {
            LocalDateTime at = parseDateTime(row.get("recordedAt"));
            if (at != null && !at.isBefore(cutoff)) {
                count++;
            }
        }
        return count;
    }

    public void syncFromLabResults(Long registerId) {
        if (!followUpProperties.preferProductionObservations()) {
            return;
        }
        try {
            List<Map<String, Object>> mappings = healthObservationMapper.selectLabMetricMappings();
            Map<String, Map<String, Object>> mappingIndex = buildMappingIndex(mappings);

            for (Map<String, Object> row : healthObservationMapper.selectInspectionResults(registerId)) {
                parseAndPersist(registerId, row, "inspection", mappingIndex);
            }
            for (Map<String, Object> row : healthObservationMapper.selectCheckResults(registerId)) {
                parseAndPersist(registerId, row, "check", mappingIndex);
            }
        } catch (DataAccessException ex) {
            log.warn("检验结果同步失败: {}", ex.getMessage());
        }
    }

    private void parseAndPersist(
        Long registerId,
        Map<String, Object> row,
        String sourceType,
        Map<String, Map<String, Object>> mappingIndex
    ) {
        String json = row.get("resultJson") != null ? String.valueOf(row.get("resultJson")) : "";
        if (json.isBlank()) {
            return;
        }
        LocalDateTime parsedAt = parseDateTime(row.get("observedAt"));
        final LocalDateTime observedAt = parsedAt != null ? parsedAt : LocalDateTime.now();
        Long sourceRefId = toLong(row.get("id"));

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isObject()) {
                root.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode valueNode = entry.getValue();
                    persistMappedValue(registerId, sourceType, key, valueNode, observedAt, sourceRefId, mappingIndex);
                });
            }
        } catch (Exception ex) {
            log.debug("跳过无法解析的检验 JSON registerId={}: {}", registerId, ex.getMessage());
        }
    }

    private void persistMappedValue(
        Long registerId,
        String sourceType,
        String rawKey,
        JsonNode valueNode,
        LocalDateTime observedAt,
        Long sourceRefId,
        Map<String, Map<String, Object>> mappingIndex
    ) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        Double numeric = extractNumber(valueNode);
        if (numeric == null) {
            return;
        }

        Map<String, Object> mapping = mappingIndex.get(sourceType + ":" + rawKey.toLowerCase(Locale.ROOT));
        if (mapping == null) {
            mapping = mappingIndex.get(sourceType + ":" + rawKey);
        }
        if (mapping == null) {
            return;
        }

        String metricCode = String.valueOf(mapping.get("metricCode"));
        String unit = mapping.get("unit") != null ? String.valueOf(mapping.get("unit")) : null;
        try {
            healthObservationMapper.insertObservation(
                registerId, observedAt, metricCode, numeric, unit, sourceType, sourceRefId, rawKey
            );
        } catch (DataAccessException ex) {
            log.debug("指标写入跳过: {}", ex.getMessage());
        }
    }

    private Map<String, Map<String, Object>> buildMappingIndex(List<Map<String, Object>> mappings) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String sourceType = String.valueOf(mapping.get("sourceType"));
            String sourceKey = String.valueOf(mapping.get("sourceKey"));
            index.put(sourceType + ":" + sourceKey.toLowerCase(Locale.ROOT), mapping);
            index.put(sourceType + ":" + sourceKey, mapping);
        }
        return index;
    }

    private Double extractNumber(JsonNode node) {
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            try {
                return Double.parseDouble(text.replaceAll("[^0-9.+-]", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (node.isObject() && node.has("value")) {
            return extractNumber(node.get("value"));
        }
        return null;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception ex) {
            try {
                return LocalDate.parse(text.substring(0, 10)).atStartOfDay();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
