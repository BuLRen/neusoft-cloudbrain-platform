package com.xikang.medtech.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class CtInferenceService {

    private final DifyAiProperties properties;

    public CtInferenceService(DifyAiProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> infer(Map<String, Object> input) {
        String bodyPart = String.valueOf(input.getOrDefault("bodyPart", "chest"));
        long seed = 42L;
        Object seedObj = input.get("randomSeed");
        if (seedObj instanceof Number number) {
            seed = number.longValue();
        }
        Random random = new Random(seed + bodyPart.hashCode());

        boolean hasAbnormality = random.nextDouble() > 0.25;
        String riskLevel = hasAbnormality ? "attention" : "normal";
        String location = "chest".equals(bodyPart) ? "右肺下叶" : "左侧基底节区";
        String techName = "chest".equals(bodyPart) ? "胸部CT" : "头颅CT";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("techId", input.get("techId"));
        result.put("techName", input.getOrDefault("examName", techName));
        result.put("techType", "check");
        result.put("hasAbnormality", hasAbnormality);
        result.put("abnormalProbability", hasAbnormality ? 0.75 + random.nextDouble() * 0.2 : 0.1);
        result.put("riskLevel", riskLevel);

        List<Map<String, Object>> findings = new ArrayList<>();
        if (hasAbnormality) {
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("findingType", "chest".equals(bodyPart) ? "ground_glass_opacity" : "low_density_lesion");
            finding.put("anatomicalLocation", location);
            finding.put("size", "约" + (8 + random.nextInt(6)) + "mm");
            finding.put("severity", "moderate");
            finding.put("confidence", 0.7 + random.nextDouble() * 0.15);
            finding.put("bbox", List.of(100 + random.nextInt(40), 200 + random.nextInt(40), 60, 60));
            findings.add(finding);
        }
        result.put("findings", findings);
        result.put(
            "resultText",
            hasAbnormality
                ? location + "见异常密度影，边界欠清，建议结合临床。"
                : "未见明确异常密度影。"
        );
        result.put(
            "aiImpression",
            hasAbnormality
                ? ("chest".equals(bodyPart) ? "考虑感染性病变可能，建议结合临床与实验室检查。" : "见低密度灶，建议随访或进一步检查。")
                : "未见明显异常。"
        );
        String ctUrl = properties.getCtInferenceUrl();
        result.put("limitations", ctUrl == null || ctUrl.isBlank()
            ? "当前为内置模拟推理，接入真实模型后替换。"
            : "辅助分析结果，不能替代医师阅片。");
        return result;
    }
}
