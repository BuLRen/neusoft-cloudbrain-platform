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
import java.util.Iterator;
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
            return production;
        }

        if (followUpProperties.isHybridMode()) {
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

    public LocalDateTime getLatestPatientGlucoseObservationAt(Long registerId) {
        try {
            return healthObservationMapper.selectLatestPatientGlucoseObservationAt(registerId);
        } catch (DataAccessException ex) {
            log.warn("读取最近居家血糖失败: {}", ex.getMessage());
            return null;
        }
    }

    public Double getLatestPatientGlucoseValue(Long registerId) {
        try {
            return healthObservationMapper.selectLatestPatientGlucoseValue(registerId);
        } catch (DataAccessException ex) {
            log.warn("读取最近居家血糖数值失败: {}", ex.getMessage());
            return null;
        }
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
            for (MetricCandidate candidate : extractMetricCandidates(root)) {
                persistCandidate(registerId, sourceType, candidate, observedAt, sourceRefId, mappingIndex);
            }
        } catch (Exception ex) {
            log.debug("跳过无法解析的检验 JSON registerId={}: {}", registerId, ex.getMessage());
        }
    }

    private List<MetricCandidate> extractMetricCandidates(JsonNode root) {
        List<MetricCandidate> candidates = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return candidates;
        }

        JsonNode structured = root.get("structuredOutput");
        if (structured == null) {
            structured = root.get("structured_output");
        }
        if (structured != null && structured.isObject()) {
            JsonNode resultItems = structured.get("resultItems");
            if (resultItems != null && resultItems.isArray()) {
                for (JsonNode item : resultItems) {
                    if (!item.isObject()) {
                        continue;
                    }
                    String itemCode = textOrNull(item.get("itemCode"));
                    String itemName = textOrNull(item.get("itemName"));
                    String rawKey = itemCode != null ? itemCode : itemName;
                    if (rawKey == null) {
                        continue;
                    }
                    Double numeric = extractNumber(item.get("value"));
                    if (numeric == null) {
                        continue;
                    }
                    String unit = textOrNull(item.get("unit"));
                    String note = itemName != null ? itemName : rawKey;
                    candidates.add(new MetricCandidate(rawKey, numeric, unit, note));
                }
            }
        }

        JsonNode values = root.get("values");
        if (values != null && values.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = values.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (isMetaField(key)) {
                    continue;
                }
                Double numeric = extractNumber(entry.getValue());
                if (numeric != null) {
                    candidates.add(new MetricCandidate(key, numeric, null, key));
                }
            }
        }

        Iterator<Map.Entry<String, JsonNode>> topLevel = root.fields();
        while (topLevel.hasNext()) {
            Map.Entry<String, JsonNode> entry = topLevel.next();
            String key = entry.getKey();
            if (isMetaField(key) || "values".equals(key) || "structuredOutput".equals(key) || "structured_output".equals(key)) {
                continue;
            }
            Double numeric = extractNumber(entry.getValue());
            if (numeric != null) {
                candidates.add(new MetricCandidate(key, numeric, null, key));
            }
        }
        return candidates;
    }

    private void persistCandidate(
        Long registerId,
        String sourceType,
        MetricCandidate candidate,
        LocalDateTime observedAt,
        Long sourceRefId,
        Map<String, Map<String, Object>> mappingIndex
    ) {
        ResolvedMetric resolved = resolveMetric(sourceType, candidate.rawKey(), candidate.unit(), mappingIndex);
        if (resolved == null) {
            return;
        }
        try {
            healthObservationMapper.insertObservation(
                registerId,
                observedAt,
                resolved.metricCode(),
                candidate.value(),
                resolved.unit(),
                sourceType,
                sourceRefId,
                candidate.note()
            );
        } catch (DataAccessException ex) {
            log.debug("指标写入跳过: {}", ex.getMessage());
        }
    }

    private ResolvedMetric resolveMetric(
        String sourceType,
        String rawKey,
        String itemUnit,
        Map<String, Map<String, Object>> mappingIndex
    ) {
        Map<String, Object> mapping = mappingIndex.get(sourceType + ":" + rawKey.toLowerCase(Locale.ROOT));
        if (mapping == null) {
            mapping = mappingIndex.get(sourceType + ":" + rawKey);
        }
        if (mapping != null) {
            String metricCode = String.valueOf(mapping.get("metricCode"));
            String unit = mapping.get("unit") != null ? String.valueOf(mapping.get("unit")) : itemUnit;
            return new ResolvedMetric(metricCode, unit);
        }
        String normalized = normalizeMetricCode(rawKey);
        if (normalized.isBlank()) {
            return null;
        }
        return new ResolvedMetric(normalized, itemUnit);
    }

    private static boolean isMetaField(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("remark")
            || lower.contains("result")
            || lower.contains("conclusion")
            || lower.contains("notice")
            || lower.contains("name")
            || lower.contains("schema")
            || lower.contains("category")
            || lower.contains("tech")
            || lower.contains("submitted");
    }

    private static String normalizeMetricCode(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String normalized = rawKey.trim()
            .replace('%', '_')
            .replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fff]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 64) {
            return "";
        }
        return normalized;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
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
        if (node == null || node.isNull()) {
            return null;
        }
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

    private record MetricCandidate(String rawKey, Double value, String unit, String note) {}

    private record ResolvedMetric(String metricCode, String unit) {}
}
