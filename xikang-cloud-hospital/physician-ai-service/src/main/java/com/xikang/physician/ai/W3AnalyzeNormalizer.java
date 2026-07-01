package com.xikang.physician.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes W3 workflow output to the {@code w3Analyze} contract.
 * W3 is result interpretation only — never a disease diagnosis step.
 */
@Component
public class W3AnalyzeNormalizer {

    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("normal", "attention", "high");
    private static final Set<String> ALLOWED_STATUSES = Set.of("normal", "high", "low", "abnormal", "positive");

    public Map<String, Object> normalize(Map<String, Object> output, Long registerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (output == null) {
            output = Map.of();
        }

        List<Map<String, Object>> summaries = normalizeSummaries(output.get("examSummaries"));
        result.put("registerId", registerId);
        result.put("clinicalImpression", normalizeClinicalImpression(output.get("clinicalImpression"), summaries));
        result.put("examSummaries", summaries);
        result.put("overallAnalysis", normalizeOverall(output.get("overallAnalysis")));
        result.put("explicitNonDiagnosis", true);
        return result;
    }

    private List<Map<String, Object>> normalizeSummaries(Object raw) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> item : listOfMaps(raw)) {
            List<Map<String, Object>> indicatorRows = normalizeIndicatorRows(item.get("indicatorRows"));
            List<String> keyFindings = normalizeKeyFindings(item.get("keyFindings"));
            if (keyFindings.isEmpty() && !indicatorRows.isEmpty()) {
                keyFindings = buildKeyFindingsFromRows(indicatorRows);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("techName", str(item.get("techName")));
            summary.put("techType", str(item.get("techType")));
            summary.put("clinicalImpression", str(item.get("clinicalImpression")));
            summary.put("indicatorRows", indicatorRows);
            summary.put("keyFindings", keyFindings);
            summary.put("interpretation", str(item.get("interpretation")));
            summary.put("riskLevel", normalizeRiskLevel(item.get("riskLevel")));
            if (!str(summary.get("techName")).isEmpty()) {
                summaries.add(summary);
            }
        }
        return summaries;
    }

    private List<Map<String, Object>> normalizeIndicatorRows(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : listOfMaps(raw)) {
            String itemName = str(item.get("itemName"));
            if (itemName.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", str(item.get("itemCode")));
            row.put("itemName", itemName);
            row.put("value", item.get("value") == null ? "" : item.get("value"));
            row.put("unit", str(item.get("unit")));
            row.put("referenceRange", str(item.get("referenceRange")));
            row.put("status", normalizeIndicatorStatus(item.get("status")));
            row.put("aiNote", str(item.get("aiNote")));
            rows.add(row);
        }
        return rows;
    }

    private List<String> buildKeyFindingsFromRows(List<Map<String, Object>> indicatorRows) {
        List<String> findings = new ArrayList<>();
        for (Map<String, Object> row : indicatorRows) {
            String status = str(row.get("status"));
            if ("normal".equals(status)) {
                continue;
            }
            String itemName = str(row.get("itemName"));
            Object value = row.get("value");
            String unit = str(row.get("unit"));
            String valueText = value == null ? "" : String.valueOf(value);
            if (!unit.isEmpty()) {
                valueText = valueText + " " + unit;
            }
            String statusText = switch (status) {
                case "high" -> "偏高";
                case "low" -> "偏低";
                case "abnormal" -> "异常";
                case "positive" -> "阳性";
                default -> status;
            };
            findings.add(itemName + " " + valueText.trim() + "（" + statusText + "）");
        }
        return findings;
    }

    private String normalizeClinicalImpression(Object raw, List<Map<String, Object>> summaries) {
        String text = str(raw);
        if (!text.isEmpty()) {
            return text;
        }
        List<String> impressions = new ArrayList<>();
        for (Map<String, Object> summary : summaries) {
            String level = str(summary.get("riskLevel"));
            if (!"attention".equals(level) && !"high".equals(level)) {
                continue;
            }
            String impression = str(summary.get("clinicalImpression"));
            if (!impression.isEmpty()) {
                impressions.add(impression);
            }
        }
        return String.join("；", impressions);
    }

    private List<String> normalizeKeyFindings(Object raw) {
        List<String> findings = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String text = str(item);
                if (!text.isEmpty()) {
                    findings.add(text);
                }
            }
        } else {
            String text = str(raw);
            if (!text.isEmpty()) {
                findings.add(text);
            }
        }
        return findings;
    }

    private String normalizeOverall(Object raw) {
        String text = str(raw);
        if (!text.isEmpty()) {
            return text;
        }
        return "暂无检查结果可供解读。";
    }

    private String normalizeRiskLevel(Object raw) {
        String level = str(raw).toLowerCase();
        if (ALLOWED_RISK_LEVELS.contains(level)) {
            return level;
        }
        if (level.contains("高") || level.contains("危")) {
            return "high";
        }
        if (level.contains("注") || level.contains("异") || level.contains("中")) {
            return "attention";
        }
        return "normal";
    }

    private String normalizeIndicatorStatus(Object raw) {
        String status = str(raw).toLowerCase();
        if (ALLOWED_STATUSES.contains(status)) {
            return status;
        }
        if ("h".equals(status) || status.contains("高") || status.contains("升")) {
            return "high";
        }
        if ("l".equals(status) || status.contains("低") || status.contains("降")) {
            return "low";
        }
        if (status.contains("阳")) {
            return "positive";
        }
        if (status.contains("异")) {
            return "abnormal";
        }
        return "normal";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
