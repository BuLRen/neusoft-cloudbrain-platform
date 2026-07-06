package com.xikang.medtech.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.critical.CriticalDetectResult;
import com.xikang.medtech.critical.CriticalItemHit;
import com.xikang.medtech.entity.CriticalValueRule;
import com.xikang.medtech.mapper.CriticalValueRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalValueDetector {

    private final CriticalValueRuleMapper ruleMapper;
    private final DifyWorkflowClient difyWorkflowClient;
    private final ObjectMapper objectMapper;

    public CriticalDetectResult detect(String techCode, Map<String, Object> resultData) {
        List<CriticalItemHit> hits = new ArrayList<>();
        hits.addAll(detectFromStructuredOutput(techCode, resultData));
        hits.addAll(detectFromValues(techCode, resultData));

        if (!hits.isEmpty()) {
            String severity = hits.stream()
                .map(CriticalItemHit::getSeverity)
                .filter("CRITICAL"::equals)
                .findFirst()
                .orElse(hits.get(0).getSeverity());
            return CriticalDetectResult.of(dedupeHits(hits), severity, "rule");
        }

        CriticalDetectResult aiResult = detectFromAi(techCode, resultData);
        if (aiResult.isSuspected()) {
            return aiResult;
        }
        return CriticalDetectResult.none();
    }

    @SuppressWarnings("unchecked")
    private List<CriticalItemHit> detectFromStructuredOutput(String techCode, Map<String, Object> resultData) {
        List<CriticalItemHit> hits = new ArrayList<>();
        Object structured = resultData.get("structuredOutput");
        if (!(structured instanceof Map<?, ?> structuredMap)) {
            return hits;
        }
        Object itemsObj = structuredMap.get("resultItems");
        if (!(itemsObj instanceof List<?> items)) {
            return hits;
        }
        List<CriticalValueRule> rules = ruleMapper.selectAllEnabled();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            String itemName = stringValue(item.get("itemName"));
            String itemCode = stringValue(item.get("itemCode"));
            BigDecimal numericValue = parseDecimal(item.get("value"));
            if (numericValue == null) {
                continue;
            }
            for (CriticalValueRule rule : rules) {
                if (!ruleMatchesTech(rule.getTechCode(), techCode)) {
                    continue;
                }
                if (!ruleMatchesItem(rule, itemName, itemCode)) {
                    continue;
                }
                CriticalItemHit hit = evaluateRule(rule, numericValue, stringValue(item.get("unit")),
                    stringValue(item.get("referenceRange")));
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private List<CriticalItemHit> detectFromValues(String techCode, Map<String, Object> resultData) {
        List<CriticalItemHit> hits = new ArrayList<>();
        Object valuesObj = resultData.get("values");
        if (!(valuesObj instanceof Map<?, ?> values)) {
            return hits;
        }
        List<CriticalValueRule> rules = ruleMapper.selectAllEnabled();
        for (CriticalValueRule rule : rules) {
            if (!ruleMatchesTech(rule.getTechCode(), techCode)) {
                continue;
            }
            if (rule.getFieldKey() == null || rule.getFieldKey().isBlank()) {
                continue;
            }
            Object raw = values.get(rule.getFieldKey());
            BigDecimal numericValue = parseDecimal(raw);
            if (numericValue == null) {
                continue;
            }
            CriticalItemHit hit = evaluateRule(rule, numericValue, rule.getUnit(), buildReferenceRange(rule));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private CriticalDetectResult detectFromAi(String techCode, Map<String, Object> resultData) {
        if (!difyWorkflowClient.isCriticalValueDetectEnabled()) {
            return CriticalDetectResult.none();
        }
        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("tech_code", techCode != null ? techCode : "");
            inputs.put("result_payload", objectMapper.writeValueAsString(resultData));
            DifyWorkflowRunResult run = difyWorkflowClient.runCriticalValueDetectBlocking(
                inputs,
                "medtech-critical-detect",
                "critical-detect"
            );
            Map<String, Object> outputs = run.getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                return CriticalDetectResult.none();
            }
            boolean isCritical = Boolean.TRUE.equals(outputs.get("is_critical"))
                || Boolean.TRUE.equals(outputs.get("isCritical"))
                || "true".equalsIgnoreCase(String.valueOf(outputs.get("is_critical")));
            if (!isCritical) {
                return CriticalDetectResult.none();
            }
            List<CriticalItemHit> hits = new ArrayList<>();
            Object itemsObj = outputs.get("critical_items");
            if (itemsObj == null) {
                itemsObj = outputs.get("criticalItems");
            }
            if (itemsObj instanceof List<?> list) {
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map) {
                        CriticalItemHit hit = new CriticalItemHit();
                        hit.setItemName(stringValue(map.get("item_name") != null ? map.get("item_name") : map.get("itemName")));
                        hit.setValue(stringValue(map.get("value")));
                        hit.setUnit(stringValue(map.get("unit")));
                        hit.setReferenceRange(stringValue(map.get("reference_range") != null ? map.get("reference_range") : map.get("referenceRange")));
                        hit.setReason(stringValue(map.get("reason")));
                        hit.setSeverity(stringValue(map.get("severity")) != null ? stringValue(map.get("severity")) : "CRITICAL");
                        hit.setRule("AI研判");
                        hits.add(hit);
                    }
                }
            }
            if (hits.isEmpty()) {
                CriticalItemHit hit = new CriticalItemHit();
                hit.setItemName(stringValue(outputs.get("item_name")) != null ? stringValue(outputs.get("item_name")) : "AI识别危急值");
                hit.setReason(stringValue(outputs.get("reason")) != null ? stringValue(outputs.get("reason")) : stringValue(outputs.get("summary")));
                hit.setSeverity("CRITICAL");
                hit.setRule("AI研判");
                hits.add(hit);
            }
            return CriticalDetectResult.of(hits, "CRITICAL", "ai");
        } catch (Exception ex) {
            log.warn("危急值 AI 识别失败，降级为无命中: {}", ex.getMessage());
            return CriticalDetectResult.none();
        }
    }

    private CriticalItemHit evaluateRule(
        CriticalValueRule rule,
        BigDecimal value,
        String unit,
        String referenceRange
    ) {
        boolean lowHit = rule.getCriticalLow() != null && value.compareTo(rule.getCriticalLow()) < 0;
        boolean highHit = rule.getCriticalHigh() != null && value.compareTo(rule.getCriticalHigh()) > 0;
        if (!lowHit && !highHit) {
            return null;
        }
        CriticalItemHit hit = new CriticalItemHit();
        hit.setItemName(rule.getItemName());
        hit.setFieldKey(rule.getFieldKey());
        hit.setValue(value.stripTrailingZeros().toPlainString());
        hit.setUnit(unit != null && !unit.isBlank() ? unit : rule.getUnit());
        hit.setReferenceRange(referenceRange != null && !referenceRange.isBlank() ? referenceRange : buildReferenceRange(rule));
        hit.setSeverity(rule.getSeverity());
        hit.setRule(lowHit ? "低于危急下限" : "高于危急上限");
        hit.setReason(hit.getItemName() + " " + hit.getValue() + (hit.getUnit() != null ? hit.getUnit() : "") + "（" + hit.getRule() + "）");
        return hit;
    }

    private boolean ruleMatchesTech(String ruleTechCode, String techCode) {
        if (ruleTechCode == null || ruleTechCode.isBlank() || "*".equals(ruleTechCode)) {
            return true;
        }
        return techCode != null && ruleTechCode.equalsIgnoreCase(techCode);
    }

    private boolean ruleMatchesItem(CriticalValueRule rule, String itemName, String itemCode) {
        if (rule.getFieldKey() != null && itemCode != null && rule.getFieldKey().equalsIgnoreCase(itemCode)) {
            return true;
        }
        if (itemName == null || rule.getItemName() == null) {
            return false;
        }
        String a = itemName.toLowerCase(Locale.ROOT);
        String b = rule.getItemName().toLowerCase(Locale.ROOT);
        return a.contains(b) || b.contains(a);
    }

    private String buildReferenceRange(CriticalValueRule rule) {
        if (rule.getCriticalLow() != null && rule.getCriticalHigh() != null) {
            return rule.getCriticalLow().stripTrailingZeros().toPlainString() + "-" + rule.getCriticalHigh().stripTrailingZeros().toPlainString();
        }
        if (rule.getCriticalLow() != null) {
            return "≥" + rule.getCriticalLow().stripTrailingZeros().toPlainString();
        }
        if (rule.getCriticalHigh() != null) {
            return "≤" + rule.getCriticalHigh().stripTrailingZeros().toPlainString();
        }
        return "";
    }

    private List<CriticalItemHit> dedupeHits(List<CriticalItemHit> hits) {
        Map<String, CriticalItemHit> deduped = new LinkedHashMap<>();
        for (CriticalItemHit hit : hits) {
            String key = (hit.getItemName() != null ? hit.getItemName() : "") + "|" + (hit.getValue() != null ? hit.getValue() : "");
            deduped.putIfAbsent(key, hit);
        }
        return new ArrayList<>(deduped.values());
    }

    private BigDecimal parseDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.replaceAll("[^0-9.+-]", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }
}
