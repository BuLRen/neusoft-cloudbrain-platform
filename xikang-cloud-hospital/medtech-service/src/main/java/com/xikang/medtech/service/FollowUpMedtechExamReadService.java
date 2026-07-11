package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.mapper.FollowUpMedtechExamReadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 只读聚合医技检验/检查（inspection_request、check_request）结果，供疗效评估展示。
 * 不向既有业务表写入任何数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpMedtechExamReadService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpMedtechExamReadMapper examReadMapper;

    public Map<String, Object> getMedtechExamBundle(Long registerId) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("registerId", registerId);
        bundle.put("exams", listExamReports(registerId));
        bundle.put("metrics", listMetricObservations(registerId, null, null, null));
        return bundle;
    }

    public List<Map<String, Object>> listExamReports(Long registerId) {
        List<Map<String, Object>> reports = new ArrayList<>();
        try {
            for (Map<String, Object> row : examReadMapper.selectInspectionExamRows(registerId)) {
                reports.add(buildExamReport(row, "inspection"));
            }
            for (Map<String, Object> row : examReadMapper.selectCheckExamRows(registerId)) {
                reports.add(buildExamReport(row, "check"));
            }
        } catch (DataAccessException ex) {
            log.warn("读取医技检查结果失败 registerId={}: {}", registerId, ex.getMessage());
            return List.of();
        }
        reports.sort(Comparator.comparing(
            (Map<String, Object> r) -> String.valueOf(r.getOrDefault("observedAt", "")),
            Comparator.reverseOrder()
        ));
        return reports;
    }

    public List<Map<String, Object>> listMetricObservations(
        Long registerId,
        LocalDate from,
        LocalDate to,
        List<String> metricKeys
    ) {
        Map<String, Map<String, Object>> catalogIndex = loadCatalogIndex();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (Map<String, Object> exam : examReadMapper.selectInspectionExamRows(registerId)) {
                collectMetricsFromJson(rows, registerId, exam, "inspection", catalogIndex);
            }
            for (Map<String, Object> exam : examReadMapper.selectCheckExamRows(registerId)) {
                collectMetricsFromJson(rows, registerId, exam, "check", catalogIndex);
            }
        } catch (DataAccessException ex) {
            log.warn("解析医技指标失败 registerId={}: {}", registerId, ex.getMessage());
            return List.of();
        }

        return rows.stream()
            .filter(row -> withinDate(row, from, to))
            .filter(row -> matchesMetricKeys(row, metricKeys))
            .sorted(Comparator
                .comparing((Map<String, Object> r) -> String.valueOf(r.get("recordDate")))
                .thenComparing(r -> String.valueOf(r.get("metricKey"))))
            .collect(Collectors.toList());
    }

    private Map<String, Object> buildExamReport(Map<String, Object> row, String sourceType) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("requestId", row.get("requestId"));
        report.put("examType", row.get("examType"));
        report.put("examTypeLabel", row.get("examTypeLabel"));
        report.put("techCode", row.get("techCode"));
        report.put("techName", row.get("techName"));
        report.put("techType", row.get("techType"));
        report.put("state", row.get("state"));
        report.put("observedAt", formatDateTime(row.get("observedAt")));

        String json = row.get("resultJson") != null ? String.valueOf(row.get("resultJson")) : "";
        ParsedExam parsed = parseExamJson(json);
        report.put("conclusion", parsed.conclusion());
        report.put("notice", parsed.notice());
        report.put("isNormal", parsed.isNormal());
        report.put("resultItems", parsed.resultItems());
        report.put("sourceType", sourceType);
        return report;
    }

    private void collectMetricsFromJson(
        List<Map<String, Object>> target,
        Long registerId,
        Map<String, Object> examRow,
        String sourceType,
        Map<String, Map<String, Object>> catalogIndex
    ) {
        String json = examRow.get("resultJson") != null ? String.valueOf(examRow.get("resultJson")) : "";
        if (json.isBlank()) {
            return;
        }
        LocalDateTime observedAt = parseDateTime(examRow.get("observedAt"));
        if (observedAt == null) {
            observedAt = LocalDateTime.now();
        }
        Long sourceRefId = toLong(examRow.get("requestId"));
        String techName = examRow.get("techName") != null ? String.valueOf(examRow.get("techName")) : "";

        try {
            JsonNode root = MAPPER.readTree(json);
            for (MetricCandidate candidate : extractMetricCandidates(root)) {
                ResolvedMetric resolved = resolveMetric(sourceType, candidate.rawKey(), candidate.unit(), catalogIndex);
                if (resolved == null) {
                    continue;
                }
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("registerId", registerId);
                metric.put("recordDate", observedAt.toLocalDate().toString());
                metric.put("recordedAt", observedAt.toString());
                metric.put("metricKey", resolved.metricCode());
                metric.put("metricValue", candidate.value());
                metric.put("unit", resolved.unit() != null ? resolved.unit() : candidate.unit());
                metric.put("source", sourceType);
                metric.put("sourceRefId", sourceRefId);
                metric.put("note", candidate.note() + (techName.isBlank() ? "" : " · " + techName));
                metric.put("displayLabel", resolved.displayLabel());
                target.add(metric);
            }
        } catch (Exception ex) {
            log.debug("跳过无法解析的医技 JSON registerId={}: {}", registerId, ex.getMessage());
        }
    }

    private Map<String, Map<String, Object>> loadCatalogIndex() {
        Map<String, Map<String, Object>> index = new HashMap<>();
        try {
            for (Map<String, Object> row : examReadMapper.selectExamMetricCatalog()) {
                String sourceType = String.valueOf(row.get("sourceType"));
                String sourceKey = String.valueOf(row.get("sourceKey"));
                index.put(sourceType + ":" + sourceKey.toLowerCase(Locale.ROOT), row);
                index.put(sourceType + ":" + sourceKey, row);
            }
        } catch (DataAccessException ex) {
            log.warn("follow_up_exam_metric_catalog 未就绪，使用代码内默认归一化: {}", ex.getMessage());
        }
        return index;
    }

    private ParsedExam parseExamJson(String json) {
        List<Map<String, Object>> items = new ArrayList<>();
        String conclusion = null;
        String notice = null;
        Boolean isNormal = null;
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode structured = root.get("structuredOutput");
            if (structured == null) {
                structured = root.get("structured_output");
            }
            if (structured != null && structured.isObject()) {
                conclusion = textOrNull(structured.get("conclusion"));
                notice = textOrNull(structured.get("notice"));
                if (structured.has("isNormal")) {
                    isNormal = structured.get("isNormal").asBoolean();
                }
                JsonNode resultItems = structured.get("resultItems");
                if (resultItems != null && resultItems.isArray()) {
                    for (JsonNode item : resultItems) {
                        if (!item.isObject()) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("itemCode", textOrNull(item.get("itemCode")));
                        row.put("itemName", textOrNull(item.get("itemName")));
                        row.put("value", item.has("value") && !item.get("value").isNull()
                            ? item.get("value").isNumber() ? item.get("value").doubleValue() : item.get("value").asText()
                            : null);
                        row.put("unit", textOrNull(item.get("unit")));
                        row.put("referenceRange", textOrNull(item.get("referenceRange")));
                        row.put("status", textOrNull(item.get("status")));
                        row.put("meaning", textOrNull(item.get("meaning")));
                        items.add(row);
                    }
                }
            }
            if (conclusion == null) {
                JsonNode values = root.get("values");
                if (values != null && values.isObject()) {
                    conclusion = textOrNull(values.get("inspectionResult"));
                    if (conclusion == null) {
                        conclusion = textOrNull(values.get("checkResult"));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("医技报告 JSON 解析失败: {}", ex.getMessage());
        }
        return new ParsedExam(items, conclusion, notice, isNormal);
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
                    candidates.add(new MetricCandidate(rawKey, numeric, textOrNull(item.get("unit")), itemName != null ? itemName : rawKey));
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
        return candidates;
    }

    private ResolvedMetric resolveMetric(
        String sourceType,
        String rawKey,
        String itemUnit,
        Map<String, Map<String, Object>> catalogIndex
    ) {
        Map<String, Object> catalog = catalogIndex.get(sourceType + ":" + rawKey.toLowerCase(Locale.ROOT));
        if (catalog == null) {
            catalog = catalogIndex.get(sourceType + ":" + rawKey);
        }
        if (catalog != null) {
            return new ResolvedMetric(
                String.valueOf(catalog.get("metricCode")),
                catalog.get("unit") != null ? String.valueOf(catalog.get("unit")) : itemUnit,
                catalog.get("displayLabel") != null ? String.valueOf(catalog.get("displayLabel")) : rawKey
            );
        }
        String normalized = normalizeMetricCode(rawKey);
        if (normalized.isBlank()) {
            return null;
        }
        return new ResolvedMetric(normalized, itemUnit, rawKey);
    }

    private static boolean withinDate(Map<String, Object> row, LocalDate from, LocalDate to) {
        String dateText = String.valueOf(row.get("recordDate"));
        if (from != null && dateText.compareTo(from.toString()) < 0) {
            return false;
        }
        if (to != null && dateText.compareTo(to.toString()) > 0) {
            return false;
        }
        return true;
    }

    private static boolean matchesMetricKeys(Map<String, Object> row, List<String> metricKeys) {
        if (metricKeys == null || metricKeys.isEmpty()) {
            return true;
        }
        return metricKeys.contains(String.valueOf(row.get("metricKey")));
    }

    private static boolean isMetaField(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("remark") || lower.contains("result") || lower.contains("conclusion")
            || lower.contains("notice") || lower.contains("name") || lower.contains("schema")
            || lower.contains("category") || lower.contains("tech") || lower.contains("submitted");
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
        return normalized.length() > 64 ? "" : normalized;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Double extractNumber(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim().replaceAll("[^0-9.+-]", ""));
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

    private static String formatDateTime(Object value) {
        LocalDateTime dt;
        if (value instanceof LocalDateTime ldt) {
            dt = ldt;
        } else if (value instanceof java.sql.Timestamp ts) {
            dt = ts.toLocalDateTime();
        } else if (value != null) {
            try {
                dt = LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        } else {
            return null;
        }
        return dt.toString();
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

    private record ResolvedMetric(String metricCode, String unit, String displayLabel) {}

    private record ParsedExam(
        List<Map<String, Object>> resultItems,
        String conclusion,
        String notice,
        Boolean isNormal
    ) {}
}
