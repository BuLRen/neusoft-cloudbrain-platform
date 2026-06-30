package com.xikang.ai.pharmacy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.pharmacy.entity.AiFollowUpPlan;
import com.xikang.ai.pharmacy.entity.AiFollowUpRecord;
import com.xikang.ai.pharmacy.mapper.AiFollowUpPlanMapper;
import com.xikang.ai.pharmacy.mapper.AiFollowUpRecordMapper;
import com.xikang.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Pharmacy Service - AI药房服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPharmacyService {

    private final AiFollowUpPlanMapper aiFollowUpPlanMapper;
    private final AiFollowUpRecordMapper aiFollowUpRecordMapper;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelId;

    private String medicationGuidePromptTemplate;

    private String getMedicationGuidePromptTemplate() {
        if (medicationGuidePromptTemplate == null) {
            medicationGuidePromptTemplate = loadPrompt("classpath:prompts/medication-guide-prompt.st");
        }
        return medicationGuidePromptTemplate;
    }

    private String drugGuidePromptTemplate;

    private String getDrugGuidePromptTemplate() {
        if (drugGuidePromptTemplate == null) {
            drugGuidePromptTemplate = loadPrompt("classpath:prompts/drug-guide-prompt.st");
        }
        return drugGuidePromptTemplate;
    }

    private String loadPrompt(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载 prompt 模板失败: {}", location, e);
            return "";
        }
    }

    // ============================================================
    // 处方级用药指导单（真 AI 生成，含降级）
    // ============================================================

    /**
     * 生成处方级用药指导单。
     *
     * @param ctx 由 pharmacy-service 组装：{registerId, patientName, diagnosis, items:[...]}
     * @return guide_content JSON 结构（items / generalAdvice / interactionsNote / generatedAt / modelVersion）
     */
    public Map<String, Object> generateMedicationGuide(Map<String, Object> ctx) {
        log.info("[用药指导单] 开始生成 | registerId={}", ctx.get("registerId"));

        String itemsBlock = buildItemsBlock(ctx);
        String prompt = getMedicationGuidePromptTemplate()
            .replace("{patientName}", nullSafe(ctx.get("patientName")))
            .replace("{diagnosis}", nullSafe(ctx.get("diagnosis")))
            .replace("{itemCount}", String.valueOf(((List<?>) ctx.getOrDefault("items", List.of())).size()))
            .replace("{itemsBlock}", itemsBlock);

        try {
            String aiReply = chatClient.prompt()
                .messages(new UserMessage(prompt))
                .call()
                .content();
            Map<String, Object> parsed = parseGuideJson(aiReply, ctx);
            parsed.put("generatedAt", LocalDateTime.now().toString());
            parsed.put("modelVersion", modelId);
            log.info("[用药指导单] AI 生成成功 | registerId={}", ctx.get("registerId"));
            return parsed;
        } catch (Exception e) {
            log.warn("[用药指导单] AI 调用失败，走降级拼接 | registerId={}, error={}",
                ctx.get("registerId"), e.getMessage());
            Map<String, Object> fallback = buildFallbackGuide(ctx);
            fallback.put("generatedAt", LocalDateTime.now().toString());
            fallback.put("modelVersion", modelId + " (fallback)");
            return fallback;
        }
    }

    private String buildItemsBlock(Map<String, Object> ctx) {
        StringBuilder sb = new StringBuilder();
        List<?> items = (List<?>) ctx.getOrDefault("items", List.of());
        for (int i = 0; i < items.size(); i++) {
            Map<?, ?> item = (Map<?, ?>) items.get(i);
            sb.append("--- 药品 ").append(i + 1).append(" ---\n");
            sb.append("drugId: ").append(item.get("drugId")).append("\n");
            sb.append("药品名: ").append(item.get("drugName")).append("\n");
            sb.append("规格: ").append(item.get("drugFormat")).append("\n");
            sb.append("剂型: ").append(item.get("drugDosage")).append("\n");
            sb.append("数量: ").append(item.get("quantity")).append("\n");
            sb.append("医生用法: ").append(item.get("usageText")).append("\n");
            sb.append("说明书用法: ").append(item.get("instructions")).append("\n");
            sb.append("禁忌症: ").append(item.get("contraindications")).append("\n");
            sb.append("不良反应: ").append(item.get("adverseReactions")).append("\n");
            sb.append("储存条件: ").append(item.get("storageConditions")).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 解析 AI 返回的 JSON。失败时抛异常让上层走降级。
     */
    private Map<String, Object> parseGuideJson(String aiText, Map<String, Object> ctx) {
        String json = extractJson(aiText);
        try {
            JsonNode root = objectMapper.readTree(json);

            // 解析 items，回填医生原始字段（防止 AI 改写）
            List<Map<String, Object>> originalItems = (List<Map<String, Object>>) ctx.getOrDefault("items", List.of());
            Map<Object, Map<String, Object>> originalByDrugId = new HashMap<>();
            for (Map<String, Object> it : originalItems) {
                originalByDrugId.put(it.get("drugId"), it);
            }

            List<Map<String, Object>> guideItems = new ArrayList<>();
            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode n : itemsNode) {
                    Map<String, Object> g = new LinkedHashMap<>();
                    Object drugId = n.has("drugId") ? n.get("drugId").asInt() : null;
                    Map<String, Object> orig = drugId != null ? originalByDrugId.get(drugId) : null;

                    g.put("drugId", drugId);
                    g.put("drugName", textOr(orig, n, "drugName", "drugName"));
                    g.put("drugFormat", textOr(orig, n, "drugFormat", "drugFormat"));
                    g.put("drugDosage", textOr(orig, n, "drugDosage", "drugDosage"));
                    g.put("quantity", orig != null ? orig.get("quantity") : null);
                    // usageText 必须用医生原话，不接受 AI 改写
                    g.put("usageText", orig != null ? orig.get("usageText") : text(n, "usageText"));
                    g.put("howToTake", text(n, "howToTake"));
                    g.put("takeWithFood", text(n, "takeWithFood"));
                    g.put("precautions", text(n, "precautions"));
                    g.put("sideEffects", text(n, "sideEffects"));
                    g.put("storage", textOr(orig, n, "storage", "storageConditions"));
                    guideItems.add(g);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", guideItems);
            result.put("generalAdvice", text(root, "generalAdvice"));
            JsonNode interNode = root.get("interactionsNote");
            result.put("interactionsNote", (interNode == null || interNode.isNull()) ? null : interNode.asText());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("解析 AI JSON 失败: " + e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        // DeepSeek 偶尔把 null 输出成字符串 "null"
        return "null".equals(s) ? null : s;
    }

    /**
     * 优先取 AI 输出；AI 为空时回退到原始字段。origField/aiField 允许不同名（如 storage vs storageConditions）。
     */
    private String textOr(Map<String, Object> orig, JsonNode aiNode, String origField, String aiField) {
        String aiVal = text(aiNode, aiField);
        if (aiVal != null && !aiVal.isBlank()) return aiVal;
        if (orig != null) {
            Object v = orig.get(origField);
            return v == null ? null : v.toString();
        }
        return null;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String nullSafe(Object o) {
        return o == null ? "未提供" : o.toString();
    }

    /**
     * 降级方案：AI 不可用时，把 drug_info 字段直接映射输出。
     */
    private Map<String, Object> buildFallbackGuide(Map<String, Object> ctx) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) ctx.getOrDefault("items", List.of());
        List<Map<String, Object>> guideItems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("drugId", item.get("drugId"));
            g.put("drugName", item.get("drugName"));
            g.put("drugFormat", item.get("drugFormat"));
            g.put("drugDosage", item.get("drugDosage"));
            g.put("quantity", item.get("quantity"));
            g.put("usageText", item.get("usageText"));
            g.put("howToTake", item.get("instructions"));
            g.put("takeWithFood", null);
            g.put("precautions", item.get("contraindications"));
            g.put("sideEffects", item.get("adverseReactions"));
            g.put("storage", item.get("storageConditions"));
            guideItems.add(g);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", guideItems);
        result.put("generalAdvice", "请按医嘱按时服药；不要自行增减剂量或停药；如有不适及时联系医生。");
        result.put("interactionsNote", items.size() >= 2
            ? "本处方含多种药品，服药间隔请遵医嘱。" : null);
        return result;
    }

    /**
     * 创建随访计划
     */
    public Map<String, Object> createFollowUpPlan(Map<String, Object> prescriptionInfo) {
        log.info("AI创建随访计划: {}", prescriptionInfo);

        Long patientId = ((Number) prescriptionInfo.get("patientId")).longValue();
        Long registerId = prescriptionInfo.get("registerId") != null
            ? ((Number) prescriptionInfo.get("registerId")).longValue()
            : null;
        Long prescriptionId = prescriptionInfo.get("prescriptionId") != null
            ? ((Number) prescriptionInfo.get("prescriptionId")).longValue()
            : null;

        // TODO: 调用AI模型分析处方并生成随访计划
        Map<String, Object> plan = new HashMap<>();
        plan.put("planType", "medication");
        plan.put("startDate", LocalDate.now().toString());
        plan.put("endDate", LocalDate.now().plusDays(7).toString());
        plan.put("frequency", "daily");
        plan.put("followUpItems", List.of(
            Map.of("question", "今天是否按时服药？", "type", "yesno"),
            Map.of("question", "是否有不良反应？", "type", "choice", "options", List.of("无", "轻微", "严重")),
            Map.of("question", "症状改善情况？", "type", "scale", "range", "1-10")
        ));
        plan.put("instructions", "请每天按时服药，如有不适应及时联系医生。");
        plan.put("planId", 1L); // 模拟ID

        // 保存随访计划
        Long planId = saveFollowUpPlan(patientId, registerId, prescriptionId, plan);
        if (planId == null) {
            throw new BusinessException(500, "随访计划保存失败");
        }

        Map<String, Object> result = new HashMap<>(plan);
        result.put("planId", planId);
        result.put("status", "created");

        return result;
    }

    /**
     * 药品级用药说明（真 AI 生成，含降级）。
     *
     * <p>基于药品名/规格/剂型调大模型，生成该类药物的通用用药常识
     * （用法、注意事项、不良反应、储存）。AI 不可用时降级为 drug_info
     * 字段透传（目前这些字段全空，但保留兜底，以后录了数据能接住）。</p>
     */
    public Map<String, Object> getMedicationGuide(Map<String, Object> drugInfo) {
        log.info("[药品级用药说明] 开始生成 | drugName={}", drugInfo.get("drugName"));

        String prompt = getDrugGuidePromptTemplate()
            .replace("{drugName}", nullSafe(drugInfo.get("drugName")))
            .replace("{drugFormat}", nullSafe(drugInfo.get("drugFormat")))
            .replace("{drugDosage}", nullSafe(drugInfo.get("drugDosage")));

        try {
            String aiReply = chatClient.prompt()
                .messages(new UserMessage(prompt))
                .call()
                .content();
            Map<String, Object> guide = parseDrugGuideJson(aiReply, drugInfo);
            guide.put("generatedAt", LocalDateTime.now().toString());
            guide.put("modelVersion", modelId);
            log.info("[药品级用药说明] AI 生成成功 | drugName={}", drugInfo.get("drugName"));
            return guide;
        } catch (Exception e) {
            log.warn("[药品级用药说明] AI 调用失败，走降级透传 | drugName={}, error={}",
                drugInfo.get("drugName"), e.getMessage());
            Map<String, Object> fallback = buildDrugGuideFallback(drugInfo);
            fallback.put("generatedAt", LocalDateTime.now().toString());
            fallback.put("modelVersion", modelId + " (fallback)");
            return fallback;
        }
    }

    /**
     * 解析药品级 AI 返回 JSON。失败抛异常让上层走降级。
     */
    private Map<String, Object> parseDrugGuideJson(String aiText, Map<String, Object> drugInfo) {
        String json = extractJson(aiText);
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> guide = new LinkedHashMap<>();
            guide.put("drugName", drugInfo.getOrDefault("drugName", ""));
            guide.put("drugFormat", drugInfo.getOrDefault("drugFormat", ""));
            guide.put("drugDosage", drugInfo.getOrDefault("drugDosage", ""));
            guide.put("usage", text(root, "usage"));
            guide.put("precautions", text(root, "precautions"));
            guide.put("sideEffects", text(root, "sideEffects"));
            guide.put("storage", text(root, "storage"));
            return guide;
        } catch (Exception e) {
            throw new RuntimeException("解析药品级 AI JSON 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 降级方案：AI 不可用时，把 drug_info 字段直接透传输出。
     */
    private Map<String, Object> buildDrugGuideFallback(Map<String, Object> drugInfo) {
        Map<String, Object> guide = new HashMap<>();
        guide.put("drugName", drugInfo.getOrDefault("drugName", ""));
        guide.put("drugFormat", drugInfo.getOrDefault("drugFormat", ""));
        guide.put("drugDosage", drugInfo.getOrDefault("drugDosage", ""));
        guide.put("usage", drugInfo.getOrDefault("instructions", ""));
        guide.put("precautions", drugInfo.getOrDefault("contraindications", ""));
        guide.put("sideEffects", drugInfo.getOrDefault("adverseReactions", ""));
        guide.put("storage", drugInfo.getOrDefault("storageConditions", ""));
        return guide;
    }

    /**
     * 处方审核
     */
    public Map<String, Object> reviewPrescription(Map<String, Object> prescription) {
        log.info("AI处方审核: {}", prescription);

        // TODO: 调用AI模型进行处方审核
        Map<String, Object> result = new HashMap<>();
        result.put("hasIssue", false);
        result.put("interactions", List.of());
        result.put("warnings", List.of());
        result.put("recommendations", List.of("处方合理，可正常发药"));
        result.put("审核状态", "通过");

        return result;
    }

    /**
     * 记录随访反馈
     */
    public void recordFollowUpFeedback(Long planId, Map<String, Object> feedback) {
        log.info("记录随访反馈: planId={}, feedback={}", planId, feedback);

        AiFollowUpPlan plan = aiFollowUpPlanMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("随访计划不存在: " + planId);
        }

        String symptomFeedback = feedback.get("symptomFeedback") != null
            ? String.valueOf(feedback.get("symptomFeedback")) : null;
        String sideEffects = feedback.get("sideEffects") != null
            ? String.valueOf(feedback.get("sideEffects")) : null;
        String symptomRelief = feedback.get("symptomRelief") != null
            ? String.valueOf(feedback.get("symptomRelief")) : "partial";

        LocalDateTime now = LocalDateTime.now();

        AiFollowUpRecord record = new AiFollowUpRecord();
        record.setFollowUpPlanId(planId);
        record.setRegisterId(plan.getRegisterId());
        record.setSymptomRelief(symptomRelief);
        record.setHasSideEffect(sideEffects != null && !sideEffects.isBlank() ? 1 : 0);
        record.setSideEffect(sideEffects);
        record.setPatientFeedback(symptomFeedback);
        record.setFollowUpTime(now);

        aiFollowUpRecordMapper.insert(record);

        plan.setPlanStatus("completed");
        aiFollowUpPlanMapper.update(plan);
    }

    /**
     * 保存随访计划
     */
    private Long saveFollowUpPlan(Long patientId, Long registerId, Long prescriptionId, Map<String, Object> plan) {
        try {
            LocalDateTime now = LocalDateTime.now();

            AiFollowUpPlan followUpPlan = new AiFollowUpPlan();
            followUpPlan.setRegisterId(registerId);
            followUpPlan.setPrescriptionId(prescriptionId);
            followUpPlan.setFollowUpType(plan.get("planType") != null
                ? String.valueOf(plan.get("planType")) : "medication");
            if (plan.get("startDate") != null) {
                followUpPlan.setPlannedDate(LocalDate.parse(String.valueOf(plan.get("startDate"))));
            }
            followUpPlan.setContentTemplate(objectMapper.writeValueAsString(plan));
            followUpPlan.setPlanStatus("pending");
            followUpPlan.setCreationTime(now);

            aiFollowUpPlanMapper.insert(followUpPlan);
            return followUpPlan.getId();
        } catch (Exception e) {
            log.error("保存随访计划失败", e);
            return null;
        }
    }

    /**
     * 获取随访计划
     */
    public AiFollowUpPlan getFollowUpPlan(Long id) {
        return aiFollowUpPlanMapper.selectById(id);
    }

    /**
     * 获取患者的随访计划
     */
    public List<AiFollowUpPlan> getPatientFollowUpPlans(Long patientId) {
        return aiFollowUpPlanMapper.selectByPatientId(patientId);
    }

    /**
     * 获取随访记录
     */
    public List<AiFollowUpRecord> getFollowUpRecords(Long planId) {
        return aiFollowUpRecordMapper.selectByPlanId(planId);
    }
}
