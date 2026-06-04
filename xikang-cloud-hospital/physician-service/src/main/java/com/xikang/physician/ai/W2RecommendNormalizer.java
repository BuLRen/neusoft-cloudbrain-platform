package com.xikang.physician.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps W2 LLM output to catalog-backed recommendations (techCode → techId, dedupe, filter hallucinations).
 */
@Component
public class W2RecommendNormalizer {

    public Map<String, Object> normalize(Map<String, Object> output, List<Map<String, Object>> availableExaminations) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (output == null) {
            output = Map.of();
        }

        Map<String, Map<String, Object>> catalog = catalogByCode(availableExaminations);

        String preliminary = str(output.get("preliminaryAssessment"));
        String note = str(output.get("notRecommendedNote"));
        List<Map<String, Object>> unmatched = listOfMaps(output.get("unmatchedSuggestions"));

        List<Map<String, Object>> recommendedIn = listOfMaps(output.get("recommendedExaminations"));
        List<Map<String, Object>> cleaned = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();

        for (Map<String, Object> item : recommendedIn) {
            String code = str(item.get("techCode"));
            if (code.isEmpty() || seenCodes.contains(code)) {
                continue;
            }
            Map<String, Object> row = catalog.get(code);
            if (row == null) {
                row = catalog.get(code.toUpperCase());
            }
            if (row == null) {
                Map<String, Object> dropped = new LinkedHashMap<>();
                dropped.put("name", str(item.get("techName")).isEmpty() ? code : str(item.get("techName")));
                dropped.put("reason", "不在 available_examinations 清单中，已剔除");
                unmatched.add(dropped);
                continue;
            }

            seenCodes.add(code);
            int priority = clampPriority(item.get("priority"));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("techId", row.get("techId"));
            out.put("techCode", str(row.get("techCode")).isEmpty() ? code : str(row.get("techCode")));
            out.put("techName", str(row.get("techName")).isEmpty() ? str(item.get("techName")) : str(row.get("techName")));
            out.put("techType", str(row.get("techType")).isEmpty() ? str(item.get("techType")) : str(row.get("techType")));
            out.put("reason", str(item.get("reason")).isEmpty() ? "结合当前临床信息建议完善相关检查。" : str(item.get("reason")));
            out.put("priority", priority);
            putOptional(out, "purpose", item.get("purpose"));
            putOptional(out, "position", item.get("position"));
            putOptional(out, "remark", item.get("remark"));
            cleaned.add(out);
        }

        cleaned.sort((a, b) -> Integer.compare(
            ((Number) a.getOrDefault("priority", 99)).intValue(),
            ((Number) b.getOrDefault("priority", 99)).intValue()
        ));

        if (cleaned.isEmpty() && note.isEmpty()) {
            note = "未从清单中匹配到可推荐项目，请医生根据临床判断手工开立或核对病历是否已保存。";
        }
        if (preliminary.isEmpty()) {
            preliminary = "已根据当前临床信息生成检查推荐，供医生审核后开立。";
        }

        result.put("preliminaryAssessment", preliminary);
        result.put("recommendedExaminations", cleaned);
        result.put("notRecommendedNote", note);
        result.put("unmatchedSuggestions", unmatched);
        return result;
    }

    private static Map<String, Map<String, Object>> catalogByCode(List<Map<String, Object>> examinations) {
        Map<String, Map<String, Object>> catalog = new HashMap<>();
        if (examinations == null) {
            return catalog;
        }
        for (Map<String, Object> item : examinations) {
            if (item == null) {
                continue;
            }
            String code = str(item.get("techCode"));
            if (code.isEmpty()) {
                continue;
            }
            catalog.put(code, item);
            catalog.put(code.toUpperCase(), item);
        }
        return catalog;
    }

    private static int clampPriority(Object value) {
        if (value instanceof Number number) {
            int p = number.intValue();
            return Math.max(1, Math.min(5, p));
        }
        try {
            int p = Integer.parseInt(String.valueOf(value).trim());
            return Math.max(1, Math.min(5, p));
        } catch (NumberFormatException ex) {
            return 3;
        }
    }

    private static void putOptional(Map<String, Object> target, String key, Object value) {
        String text = str(value);
        if (!text.isEmpty()) {
            target.put(key, text);
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }
}
