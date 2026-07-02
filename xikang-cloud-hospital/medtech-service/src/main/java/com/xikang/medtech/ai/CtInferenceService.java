package com.xikang.medtech.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class CtInferenceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        Map<String, Object> qcResult = parseImagingAnalysis(input.get("imagingAnalysisResult"));
        String qualityNotice = buildQualityNotice(qcResult);

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

        String baseFindings = hasAbnormality
            ? location + "见异常密度影，边界欠清。"
            : "chest".equals(bodyPart)
                ? "胸廓对称，双肺野清晰，心影大小形态未见明显异常，纵隔居中，未见明显肿大淋巴结。"
                : "颅骨结构完整，脑实质密度均匀，脑室系统未见明显扩张，中线结构居中。";

        String impression = hasAbnormality
            ? ("chest".equals(bodyPart) ? "考虑感染性病变可能，建议结合临床与实验室检查。" : "见低密度灶，建议随访或进一步检查。")
            : "未见明显异常。";

        String conclusion = hasAbnormality
            ? ("chest".equals(bodyPart)
                ? "胸部 CT 平扫见右肺下叶异常密度影，建议结合临床进一步评估。"
                : "头颅 CT 平扫见低密度灶，建议随访或 MRI 进一步检查。")
            : ("chest".equals(bodyPart)
                ? "胸部 CT 平扫未见明显异常，建议结合临床。"
                : "头颅 CT 平扫未见明显异常，建议结合临床。");

        String findingsText = appendQualityNotice(baseFindings, qualityNotice);
        result.put("resultText", findingsText);
        result.put("aiImpression", impression);
        result.put("aiConclusion", conclusion);

        String ctUrl = properties.getCtInferenceUrl();
        String baseLimitation = ctUrl == null || ctUrl.isBlank()
            ? "当前为内置模拟推理，接入真实模型后替换。"
            : "辅助分析结果，不能替代医师阅片。";
        if (qualityNotice != null) {
            baseLimitation = baseLimitation + " " + qualityNotice;
        }
        result.put("limitations", baseLimitation);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseImagingAnalysis(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (raw instanceof String json && !json.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String buildQualityNotice(Map<String, Object> qcResult) {
        if (qcResult == null || qcResult.isEmpty()) {
            return null;
        }
        boolean hasArtifact = Boolean.TRUE.equals(qcResult.get("has_artifact"));
        String severity = qcResult.get("severity") != null ? String.valueOf(qcResult.get("severity")) : "";
        if (!hasArtifact && !"moderate".equals(severity) && !"severe".equals(severity)) {
            return "影像质控：未见明显伪影，图像质量可接受。";
        }

        List<String> issues = new ArrayList<>();
        Map<String, Object> types = qcResult.get("artifact_types") instanceof Map<?, ?> typeMap
            ? (Map<String, Object>) typeMap
            : null;
        if (types != null) {
            double metal = toDouble(types.get("metal"));
            if (metal >= 0.4) {
                issues.add("金属伪影");
            }
            if (toDouble(types.get("beam_hardening")) >= 0.5) {
                issues.add("线束硬化");
            }
            if (toDouble(types.get("ring")) >= 0.5) {
                issues.add("环形伪影");
            }
        }
        if (issues.isEmpty()) {
            issues.add("伪影");
        }
        return "影像质量提示：检测到" + String.join("、", issues) + "，部分区域判读可能受限，请结合原始影像综合判断。";
    }

    private static String appendQualityNotice(String findings, String qualityNotice) {
        if (qualityNotice == null || qualityNotice.isBlank()) {
            return findings;
        }
        return findings + "\n\n【质控参考】" + qualityNotice;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
