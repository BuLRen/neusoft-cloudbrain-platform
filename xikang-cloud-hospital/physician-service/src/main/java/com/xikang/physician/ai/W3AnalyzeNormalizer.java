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

    public Map<String, Object> normalize(Map<String, Object> output, Long registerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (output == null) {
            output = Map.of();
        }

        result.put("registerId", registerId);
        result.put("examSummaries", normalizeSummaries(output.get("examSummaries")));
        result.put("overallAnalysis", normalizeOverall(output.get("overallAnalysis")));
        result.put("explicitNonDiagnosis", true);
        return result;
    }

    private List<Map<String, Object>> normalizeSummaries(Object raw) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> item : listOfMaps(raw)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("techName", str(item.get("techName")));
            summary.put("keyFindings", normalizeKeyFindings(item.get("keyFindings")));
            summary.put("interpretation", str(item.get("interpretation")));
            summary.put("riskLevel", normalizeRiskLevel(item.get("riskLevel")));
            if (!str(summary.get("techName")).isEmpty()) {
                summaries.add(summary);
            }
        }
        return summaries;
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
