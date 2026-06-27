package com.xikang.medtech.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CaseSummaryFallbackEngine {

    public Map<String, Object> generate(Map<String, Object> inputs) {
        String name = text(inputs, "patientName", "患者");
        String diagnosis = text(inputs, "diagnosis", "待补充");
        String chiefComplaint = text(inputs, "chiefComplaint", "—");
        boolean observedToday = Boolean.TRUE.equals(inputs.get("observedToday"));

        StringBuilder summary = new StringBuilder();
        summary.append("## 病例摘要（系统生成）\n\n");
        summary.append("- **患者**：").append(name).append("\n");
        summary.append("- **主要诊断**：").append(diagnosis).append("\n");
        summary.append("- **主诉**：").append(chiefComplaint).append("\n");
        summary.append("- **今日观察**：").append(observedToday ? "已确认" : "待确认").append("\n");
        summary.append("- **近30天指标**：已纳入随访监测数据\n");

        String advice = observedToday
            ? "继续当前康复方案，保持规律随访与指标记录；如有新发或加重症状请及时联系医生。"
            : "请完成今日疗效观察并在随访工作台确认；关注指标波动与症状变化。";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseSummary", summary.toString());
        result.put("medicalAdvice", advice);
        result.put("riskAlerts", List.of("本总结由内置规则引擎生成，需医生审核后使用"));
        result.put("followUpFocus", List.of("指标趋势", "症状变化", "用药依从性"));
        result.put("confidence", 0.65);
        result.put("modelId", "fallback-v1");
        result.put("source", "fallback");
        return result;
    }

    public Map<String, Object> generateMedicalReply(Map<String, Object> inputs) {
        String message = text(inputs, "patientMessage", "").toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        if (message.isBlank()) {
            result.put("reply", "请描述您当前的症状或随访相关问题，我将基于您的随访记录为您解答。");
            result.put("refused", false);
            result.put("source", "fallback");
            return result;
        }

        if (isOffTopic(message)) {
            result.put("reply", "我是医疗随访助手，仅可回答与您康复、用药、症状及随访相关的问题。如有紧急不适，请尽快联系医生或就医。");
            result.put("refused", true);
            result.put("refusalReason", "非医疗随访范围");
            result.put("source", "fallback");
            return result;
        }

        String name = text(inputs, "patientName", "您");
        result.put("reply", name + "，已收到您的反馈。根据当前随访记录，请继续按医嘱用药并记录每日症状；若症状持续或加重，请在医患沟通中留言，医生会尽快回复。");
        result.put("refused", false);
        result.put("source", "fallback");
        return result;
    }

    private boolean isOffTopic(String message) {
        return message.contains("天气") || message.contains("股票") || message.contains("笑话")
            || message.contains("游戏") || message.contains("政治");
    }

    private String text(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }
}
