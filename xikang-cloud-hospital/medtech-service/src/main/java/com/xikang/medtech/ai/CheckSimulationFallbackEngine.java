package com.xikang.medtech.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class CheckSimulationFallbackEngine {

    public Map<String, Object> simulateSingleExam(Map<String, Object> exam) {
        String checkName = str(exam.get("checkName"));
        if (checkName.isEmpty()) {
            checkName = str(exam.get("techName"));
        }
        boolean isNormal = parseBoolean(exam.get("isNormal"), false);

        Random random = new Random(42L);

        Map<String, Object> structured = buildStructuredOutput(checkName, isNormal, random);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("structured_output", structured);
        out.put("resultText", structured.get("conclusion"));
        return out;
    }

    private Map<String, Object> buildStructuredOutput(String checkName, boolean isNormal, Random random) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("checkName", checkName.isEmpty() ? "检查" : checkName);
        structured.put("isNormal", isNormal);
        structured.put("simulatedForDiseases", isNormal ? List.of() : List.of("上呼吸道感染"));

        List<Map<String, Object>> items = new ArrayList<>();
        if (checkName.contains("血常规") || checkName.contains("C反应蛋白") || checkName.isEmpty()) {
            items.add(item("WBC", "白细胞", isNormal ? 6.5 : 12.0 + random.nextDouble() * 3,
                "10^9/L", "4-10", isNormal ? "normal" : "high",
                isNormal ? "白细胞计数在正常范围" : "白细胞升高，提示可能存在感染或炎症"));
            items.add(item("NEUT%", "中性粒细胞比例", isNormal ? 58 : 72 + random.nextInt(8),
                "%", "50-70", isNormal ? "normal" : "high",
                isNormal ? "中性粒细胞比例正常" : "中性粒细胞比例偏高"));
            items.add(item("HGB", "血红蛋白", isNormal ? 138 : 125 + random.nextInt(10),
                "g/L", "120-160", "normal", "血红蛋白未见明显异常"));
            items.add(item("PLT", "血小板", isNormal ? 210 : 195 + random.nextInt(30),
                "10^9/L", "100-300", "normal", "血小板计数在正常范围"));
        } else {
            items.add(item("RESULT", checkName, isNormal ? 1 : 2,
                "-", "-", isNormal ? "normal" : "high",
                isNormal ? checkName + "未见明显异常" : checkName + "可见异常改变"));
        }

        structured.put("resultItems", items);
        structured.put("conclusion", isNormal
            ? checkName + "：各主要指标均在参考范围内，未见明显异常。"
            : checkName + "：部分指标偏离参考范围，建议结合临床症状综合判断。");
        structured.put("notice", isNormal
            ? "本次为系统内置模拟结果，仅供演示。"
            : "异常指标需结合病史与其他检查综合评估；本次为内置模拟结果。");
        return structured;
    }

    private static Map<String, Object> item(
        String code,
        String name,
        double value,
        String unit,
        String referenceRange,
        String status,
        String meaning
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemCode", code);
        row.put("itemName", name);
        row.put("value", Math.round(value * 10) / 10.0);
        row.put("unit", unit);
        row.put("referenceRange", referenceRange);
        row.put("status", status);
        row.put("meaning", meaning);
        return row;
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return defaultValue;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
