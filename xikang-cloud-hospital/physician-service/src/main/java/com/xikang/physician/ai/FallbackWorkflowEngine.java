package com.xikang.physician.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Contract-compliant fallback when Dify is not configured (v3 pipelines).
 */
@Component
public class FallbackWorkflowEngine {

    public Map<String, Object> runW1(Map<String, Object> input) {
        String inputMode = String.valueOf(input.getOrDefault("inputMode", "long_text"));
        if ("pre_consultation".equals(inputMode) && input.get("structuredRecord") instanceof Map<?, ?> existing) {
            return JsonMapUtils.asMap(existing);
        }

        Map<String, Object> register = JsonMapUtils.asMap(input.get("patientInfoFromRegister"));
        Long registerId = toLong(input.get("registerId"));

        Map<String, Object> patientInfo = new LinkedHashMap<>();
        patientInfo.put("realName", register.getOrDefault("realName", "未知"));
        patientInfo.put("idCard", register.getOrDefault("idCard", null));
        patientInfo.put("gender", register.getOrDefault("gender", "未知"));
        patientInfo.put("age", register.get("age"));
        patientInfo.put("caseNumber", register.getOrDefault("caseNumber", ""));

        String chiefComplaint = "";
        String presentIllness = "";
        String history = "既往体健";
        String allergy = "无已知过敏";
        String physique = "";
        String presentTreat = "";
        String symptomDuration = "未知";
        String longText = null;

        if ("doctor_form".equals(inputMode)) {
            Map<String, Object> form = JsonMapUtils.asMap(input.get("doctorForm"));
            chiefComplaint = str(form.get("chiefComplaint"));
            presentIllness = str(form.get("presentIllness"));
            history = strOr(form.get("history"), history);
            allergy = strOr(form.get("allergy"), allergy);
            physique = str(form.get("physique"));
            presentTreat = str(form.get("presentTreat"));
            symptomDuration = strOr(form.get("symptomDuration"), symptomDuration);
        } else {
            longText = str(input.get("longText"));
            chiefComplaint = extractChiefComplaint(longText);
            presentIllness = longText != null && longText.length() > 80 ? longText.substring(0, Math.min(280, longText.length())) : longText;
            if (longText != null && longText.contains("过敏")) {
                allergy = longText.contains("青霉素") ? "青霉素过敏" : "有药物过敏史，详见口述";
            }
            if (longText != null && longText.contains("天")) {
                symptomDuration = "约3天";
            }
        }

        Map<String, Object> rawSource = new LinkedHashMap<>();
        rawSource.put("inputMode", inputMode);
        if (longText != null) {
            rawSource.put("longText", longText);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("patientInfo", patientInfo);
        out.put("chiefComplaint", chiefComplaint.isBlank() ? "不适待查" : chiefComplaint);
        out.put("symptomDuration", symptomDuration);
        out.put("presentIllness", presentIllness != null && !presentIllness.isBlank() ? presentIllness : chiefComplaint);
        out.put("presentTreat", presentTreat);
        out.put("history", history);
        out.put("allergy", allergy);
        out.put("physique", physique);
        out.put("preliminaryImpression", inferPreliminaryImpression(chiefComplaint));
        out.put("rawSource", rawSource);
        return out;
    }

    public Map<String, Object> runPreliminaryDiagnosis(Map<String, Object> input) {
        String text = str(input.get("text"));
        boolean preHandle = Boolean.TRUE.equals(input.get("preHandle"));
        Long registerId = toLong(input.get("registerId"));

        String chiefComplaint = preHandle ? extractChiefComplaintFromRecord(text) : extractChiefComplaint(text);
        String diagnosisText = inferPreliminaryImpression(chiefComplaint.isBlank() ? text : chiefComplaint);
        String basis = preHandle
            ? "基于结构化病历信息，结合主诉与病史进行初步推断。"
            : "基于患者自然语言描述进行初步推断，建议结合查体与检查进一步确认。";

        List<Map<String, Object>> suggested = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("diseaseName", diagnosisText.contains("呼吸道") ? "急性呼吸道感染待排" : "症状待查");
        suggested.add(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("diagnosisText", diagnosisText);
        out.put("diagnosisBasis", basis);
        out.put("confidence", 72.0);
        out.put("suggestedDiseases", suggested);
        out.put("preHandle", preHandle);
        return out;
    }

    private static String extractChiefComplaintFromRecord(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int idx = text.indexOf("主诉");
        if (idx >= 0) {
            String slice = text.substring(idx, Math.min(text.length(), idx + 80));
            return slice.replace("主诉：", "").replace("主诉:", "").trim();
        }
        return text.length() > 60 ? text.substring(0, 60) : text;
    }

    public Map<String, Object> runW2(Map<String, Object> input) {
        Map<String, Object> record = JsonMapUtils.asMap(input.get("structuredRecord"));
        List<Map<String, Object>> available = listOfMaps(input.get("availableExaminations"));
        Long registerId = toLong(input.get("registerId"));

        String assessment = "结合主诉「" + record.getOrDefault("chiefComplaint", "")
            + "」，" + record.getOrDefault("preliminaryImpression", "需进一步检查评估。");

        List<Map<String, Object>> recommended = new ArrayList<>();
        boolean coughLike = containsAny(str(record.get("chiefComplaint")) + str(record.get("presentIllness")), "咳嗽", "发热", "低热", "咽痛");

        for (Map<String, Object> exam : available) {
            String name = str(exam.get("techName"));
            String category = str(exam.get("category"));
            if (coughLike && (name.contains("血常规") || "lab".equals(category) && name.contains("常规"))) {
                recommended.add(recommendation(exam, "评估感染与炎症程度"));
            }
            if (coughLike && (name.contains("C反应蛋白") || name.contains("CRP"))) {
                recommended.add(recommendation(exam, "辅助判断炎症程度"));
            }
            if (coughLike && (category.contains("chest") || name.contains("胸"))) {
                recommended.add(recommendation(exam, "排除肺部浸润、肺炎"));
            }
        }

        if (recommended.isEmpty() && !available.isEmpty()) {
            recommended.add(recommendation(available.get(0), "基础筛查"));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("preliminaryAssessment", assessment);
        out.put("recommendedExaminations", recommended.stream().limit(4).toList());
        out.put("notRecommendedNote", "推荐项均来自当前可开展检查清单。");
        return out;
    }

    public Map<String, Object> runW2b(Map<String, Object> input) {
        List<Map<String, Object>> ordered = listOfMaps(input.get("orderedExaminations"));
        long seed = 42L;
        Map<String, Object> profile = JsonMapUtils.asMap(input.get("simulationProfile"));
        if (profile.get("randomSeed") instanceof Number number) {
            seed = number.longValue();
        }
        Random random = new Random(seed);

        List<Map<String, Object>> simulated = new ArrayList<>();
        for (Map<String, Object> exam : ordered) {
            String category = str(exam.get("category"));
            if (category.contains("imaging_ct")) {
                continue;
            }
            String name = str(exam.get("techName"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("techId", exam.get("techId"));
            row.put("techName", name);
            row.put("techType", exam.get("techType"));
            row.put("resultState", "completed");
            if (name.contains("血常规")) {
                double wbc = 10 + random.nextDouble() * 5;
                row.put("resultText", String.format("WBC %.1f×10^9/L, NEUT%% %.0f%%, HGB 142g/L, PLT 210×10^9/L", wbc, 65 + random.nextInt(15)));
                row.put("structuredItems", List.of(
                    Map.of("code", "WBC", "name", "白细胞", "value", String.format("%.1f", wbc), "unit", "10^9/L", "reference", "4-10", "flag", "H"),
                    Map.of("code", "NEUT%", "name", "中性粒细胞比例", "value", String.valueOf(65 + random.nextInt(15)), "unit", "%", "reference", "50-70", "flag", "H")
                ));
            } else if (name.contains("CRP") || name.contains("反应蛋白")) {
                int crp = 8 + random.nextInt(20);
                row.put("resultText", "CRP " + crp + " mg/L");
                row.put("structuredItems", List.of(
                    Map.of("code", "CRP", "name", "C反应蛋白", "value", String.valueOf(crp), "unit", "mg/L", "reference", "<8", "flag", "H")
                ));
            } else {
                row.put("resultText", name + "：未见明显异常。");
                row.put("structuredItems", List.of());
            }
            simulated.add(row);
        }

        return Map.of("simulatedResults", simulated);
    }

    public Map<String, Object> runW3(Map<String, Object> input) {
        Long registerId = toLong(input.get("registerId"));
        List<Map<String, Object>> allResults = listOfMaps(input.get("allResults"));
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (Map<String, Object> result : allResults) {
            String name = str(result.get("techName"));
            String resultText = str(result.get("resultText"));
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("techId", result.get("techId"));
            summary.put("techName", name);
            List<String> keyFindings = new ArrayList<>();
            String interpretation;
            String riskLevel = "normal";

            if (name.contains("血常规") || resultText.contains("WBC")) {
                keyFindings.add("白细胞升高");
                keyFindings.add("中性粒细胞比例升高");
                interpretation = "白细胞及中性粒细胞比例升高，提示可能存在感染或炎症反应，建议结合临床症状综合判断。";
                riskLevel = "attention";
            } else if (name.contains("CT") || result.get("imagingAi") != null) {
                keyFindings.add("影像学可见异常密度影");
                interpretation = "影像学所见需结合实验室指标与症状，注意排除肺炎等病变。";
                riskLevel = "attention";
            } else if (resultText.contains("CRP")) {
                keyFindings.add("C反应蛋白升高");
                interpretation = "炎症指标升高，提示体内可能存在炎症反应。";
                riskLevel = "attention";
            } else {
                interpretation = name + "未见明显异常指标。";
            }
            summary.put("keyFindings", keyFindings);
            summary.put("interpretation", interpretation);
            summary.put("riskLevel", riskLevel);
            summaries.add(summary);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("examSummaries", summaries);
        out.put(
            "overallAnalysis",
            summaries.isEmpty()
                ? "暂无检查结果可供分析。"
                : "目前实验室与影像检查支持存在炎症/感染可能，尚未形成唯一确诊结论，需进入诊断推理步骤。"
        );
        out.put("explicitNonDiagnosis", true);
        return out;
    }

    public Map<String, Object> runW4(Map<String, Object> input) {
        Long registerId = toLong(input.get("registerId"));
        Map<String, Object> record = JsonMapUtils.asMap(input.get("structuredRecord"));
        List<Map<String, Object>> catalog = listOfMaps(input.get("diseaseCatalog"));

        String primaryName = "上呼吸道感染";
        String primaryIcd = "J06.9";
        Long primaryDiseaseId = null;
        for (Map<String, Object> d : catalog) {
            if (str(d.get("diseaseName")).contains("上呼吸道")) {
                primaryName = str(d.get("diseaseName"));
                primaryIcd = str(d.get("icd"));
                primaryDiseaseId = toLong(d.get("diseaseId"));
                break;
            }
        }

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("diseaseId", primaryDiseaseId);
        primary.put("diseaseName", primaryName);
        primary.put("recommendIcd", primaryIcd);
        primary.put("probability", 90.0);
        primary.put(
            "diagnosisBasis",
            "结合「" + record.getOrDefault("chiefComplaint", "") + "」及检查分析，以上呼吸道感染可能性最大。"
        );

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("diseaseName", "流行性感冒");
        diff.put("recommendIcd", "J11.1");
        diff.put("probability", 10.0);
        diff.put("diagnosisBasis", "症状重叠，未行病原学检查，不能排除。");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("primaryDiagnosis", primary);
        out.put("differentialDiagnoses", List.of(diff));
        out.put("clinicalAdvice", "对症支持治疗，观察体温与症状变化，必要时复诊。");
        out.put("confidenceScore", 0.85);
        return out;
    }

    private static Map<String, Object> recommendation(Map<String, Object> exam, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("techId", exam.get("techId"));
        row.put("techName", exam.get("techName"));
        row.put("techType", exam.get("techType"));
        row.put("reason", reason);
        row.put("priority", 1);
        return row;
    }

    private static String extractChiefComplaint(String longText) {
        if (longText == null || longText.isBlank()) {
            return "";
        }
        if (longText.contains("咳嗽")) {
            return "咳嗽、低热数日";
        }
        if (longText.contains("头痛")) {
            return "头痛数日";
        }
        return longText.length() > 30 ? longText.substring(0, 30) + "…" : longText;
    }

    private static String inferPreliminaryImpression(String chiefComplaint) {
        if (chiefComplaint.contains("咳嗽") || chiefComplaint.contains("发热")) {
            return "急性呼吸道感染待排";
        }
        return "症状待进一步评估";
    }

    private static boolean containsAny(String text, String... parts) {
        if (text == null) {
            return false;
        }
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).collect(Collectors.toList());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String strOr(Object value, String fallback) {
        String text = str(value);
        return text.isBlank() ? fallback : text;
    }
}
