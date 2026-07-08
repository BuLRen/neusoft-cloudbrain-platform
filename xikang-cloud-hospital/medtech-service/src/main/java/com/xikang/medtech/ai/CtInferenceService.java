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
        Map<String, Object> segmentationResult = parseImagingAnalysis(input.get("imagingSegmentationResult"));
        String segmentationNotice = buildSegmentationNotice(segmentationResult);

        boolean hasAbnormality = random.nextDouble() > 0.25;
        if (segmentationResult != null) {
            Object countObj = extractSummaryField(segmentationResult, "lesionCount");
            int lesionCount = countObj instanceof Number number ? number.intValue() : 0;
            if (lesionCount > 0) {
                hasAbnormality = true;
            }
        }
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
        if (segmentationResult != null) {
            findings.addAll(buildFindingsFromSegmentation(segmentationResult));
        }
        if (hasAbnormality && findings.isEmpty()) {
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
            ? buildAbnormalFindingsText(bodyPart, location, findings, segmentationNotice)
            : "chest".equals(bodyPart)
                ? "胸廓对称，双肺野清晰，心影大小形态未见明显异常，纵隔居中，未见明显肿大淋巴结。"
                : "颅骨结构完整，脑实质密度均匀，脑室系统未见明显扩张，中线结构居中。";

        String impression = hasAbnormality
            ? buildAbnormalImpression(bodyPart, findings, segmentationNotice)
            : "未见明显异常。";

        String conclusion = hasAbnormality
            ? buildAbnormalConclusion(bodyPart, findings, segmentationNotice)
            : ("chest".equals(bodyPart)
                ? "胸部 CT 平扫未见明显异常，建议结合临床。"
                : "头颅 CT 平扫未见明显异常，建议结合临床。");

        String findingsText = appendQualityNotice(baseFindings, qualityNotice);
        if (segmentationNotice != null && !segmentationNotice.isBlank()) {
            findingsText = findingsText + "\n\n【AI 病灶标注】" + segmentationNotice;
        }
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
        if (segmentationNotice != null) {
            baseLimitation = baseLimitation + " AI 病灶标注仅用于教学演示。";
        }
        result.put("limitations", baseLimitation);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildFindingsFromSegmentation(Map<String, Object> segmentationResult) {
        List<Map<String, Object>> findings = new ArrayList<>();
        Object lesionsObj = segmentationResult.get("lesions");
        if (!(lesionsObj instanceof List<?> lesionList)) {
            return findings;
        }
        for (Object item : lesionList) {
            if (!(item instanceof Map<?, ?> lesionMap)) {
                continue;
            }
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("findingType", "nodule_like");
            finding.put("anatomicalLocation", lesionMap.get("label"));
            Object diameter = lesionMap.get("diameterMm");
            finding.put("size", diameter != null ? "约" + diameter + "mm" : "约8mm");
            finding.put("severity", "moderate");
            finding.put("confidence", lesionMap.get("confidence"));
            Object sliceIndex = lesionMap.get("sliceIndex");
            if (sliceIndex != null) {
                finding.put("sliceIndex", sliceIndex);
            }
            findings.add(finding);
        }
        return findings;
    }

    @SuppressWarnings("unchecked")
    private String buildSegmentationNotice(Map<String, Object> segmentationResult) {
        if (segmentationResult == null || segmentationResult.isEmpty()) {
            return null;
        }
        Object summaryObj = segmentationResult.get("summary");
        Map<String, Object> summary = summaryObj instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : segmentationResult;
        int lesionCount = 0;
        Object countObj = summary.get("lesionCount");
        if (countObj instanceof Number number) {
            lesionCount = number.intValue();
        }
        if (lesionCount <= 0) {
            return "AI 未检出明显疑似病灶（演示用途）。";
        }
        Object maxDiameter = summary.get("maxDiameterMm");
        String diameterText = maxDiameter != null ? String.valueOf(maxDiameter) : "-";
        return "AI 检出 " + lesionCount + " 处疑似病灶，最大径约 " + diameterText
            + " mm（仅用于教学演示，非临床诊断依据）。";
    }

    private static Object extractSummaryField(Map<String, Object> segmentationResult, String key) {
        Object summary = segmentationResult.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            return summaryMap.get(key);
        }
        return segmentationResult.get(key);
    }

    @SuppressWarnings("unchecked")
    private static String buildAbnormalFindingsText(
        String bodyPart,
        String location,
        List<Map<String, Object>> findings,
        String segmentationNotice
    ) {
        if (!findings.isEmpty()) {
            Map<String, Object> first = findings.get(0);
            String label = String.valueOf(first.getOrDefault("anatomicalLocation", "疑似病灶"));
            String size = String.valueOf(first.getOrDefault("size", "约8mm"));
            Object sliceIndex = first.get("sliceIndex");
            String sliceText = sliceIndex != null ? "（轴位第 " + sliceIndex + " 层）" : "";
            return label + sliceText + "见异常密度影，大小" + size + "，边界欠清。";
        }
        return location + "见异常密度影，边界欠清。";
    }

    private static String buildAbnormalImpression(
        String bodyPart,
        List<Map<String, Object>> findings,
        String segmentationNotice
    ) {
        if (!findings.isEmpty()) {
            return "chest".equals(bodyPart)
                ? "考虑结节样病变可能，建议结合临床与随访。"
                : "见异常密度灶，建议随访或进一步检查。";
        }
        return "chest".equals(bodyPart)
            ? "考虑感染性病变可能，建议结合临床与实验室检查。"
            : "见低密度灶，建议随访或进一步检查。";
    }

    private static String buildAbnormalConclusion(
        String bodyPart,
        List<Map<String, Object>> findings,
        String segmentationNotice
    ) {
        if (!findings.isEmpty()) {
            return "chest".equals(bodyPart)
                ? "胸部 CT 平扫见疑似结节样病灶，建议结合临床进一步评估（演示用途）。"
                : "头颅 CT 平扫见异常密度灶，建议随访或 MRI 进一步检查（演示用途）。";
        }
        return "chest".equals(bodyPart)
            ? "胸部 CT 平扫见异常密度影，建议结合临床进一步评估。"
            : "头颅 CT 平扫见低密度灶，建议随访或 MRI 进一步检查。";
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
