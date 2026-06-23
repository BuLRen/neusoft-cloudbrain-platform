package com.xikang.ai.triage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.triage.entity.AiTriageRecord;
import com.xikang.ai.triage.mapper.AiTriageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Triage Service - AI导诊服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTriageService {

    private final ChatClient chatClient;
    private final AiTriageRecordMapper aiTriageRecordMapper;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是一个专业的医疗分诊AI助手。根据患者描述的症状，完成以下判断并返回JSON。

        【科室列表 - 必须严格按此ID返回】
        临床科室：
        - 1=内科, 2=呼吸内科, 3=心血管内科, 4=消化内科, 5=神经内科, 6=肾内科, 7=内分泌科
        - 8=外科, 9=骨科, 10=妇产科, 11=儿科, 12=新生儿科, 13=眼科, 14=耳鼻咽喉科, 15=口腔科
        - 16=皮肤科, 17=中医科, 18=肿瘤科, 19=急诊科, 20=康复医学科
        医技科室：
        - 35=放射科, 36=超声科, 37=检验科, 38=输血科, 39=病理科, 40=处置室, 41=内镜中心

        【紧迫等级（五级分诊标准）】
        - I级 急危：危及生命，如心肌梗死、大出血、昏迷、呼吸衰竭 → 立即拨打120/去急诊
        - II级 急重：潜在危及生命，15-60分钟内需处理，如剧烈胸痛伴出汗、昏迷前兆
        - III级 紧急：需2-4小时内处理，如持续高热、胸闷呼吸困难
        - IV级 次紧急：需24-72小时内处理，如普通感冒、腹泻、轻中度疼痛
        - V级 非紧急：可择期处理，如慢性轻度症状、定期复查

        【挂号级别推荐（根据症状严重程度）】
        - 1=普通号：适用于症状轻微、病程短、首次就诊、慢性病常规复查等，如普通感冒、轻度头痛、体检咨询
        - 2=专家号：适用于症状明显、病程较长、需要专科详细诊疗的情况，如反复腹痛2周、长期咳嗽不愈
        - 3=主任医师号：适用于症状严重、疑似复杂或疑难疾病、需要高级别医师诊治的情况，如长期不明原因发热、体重明显下降、疑似肿瘤

        【红旗征（出现以下症状必须立即去急诊）】常见包括：
        - 呕血、黑便（消化道出血）
        - 剧烈腹痛伴板状腹（消化道穿孔）
        - 胸痛伴出汗、放射至左臂/下颌（心绞痛/心梗）
        - 意识障碍、言语不清、偏瘫（脑卒中）
        - 呼吸困难伴发绀、无法说话完整句子（呼吸衰竭）
        - 高热伴抽搐、颈项强直（脑膜炎）
        - 外伤大出血无法止血、骨折暴露
        - 过敏性休克（全身荨麻疹伴呼吸困难）
        - 孕晚期阴道大量出血（宫外孕/胎盘早剥）

        请根据患者描述的症状，严格按以下JSON格式返回，仅返回JSON，不要包含任何其他文字：
        {
            "urgencyLevel": "I/II/III/IV/V",
            "urgencyAdvice": "一句话行动建议，如：建议48小时内到消化内科门诊就诊",
            "recommendedDepartment": "推荐科室名称",
            "recommendedDepartmentId": 科室ID(数字，必须从上面的科室列表中选择),
            "departmentReason": "分诊依据说明，如：胃痛伴反酸通常为消化系统症状，优先推荐消化内科",
            "recommendedRegistLevelId": 挂号级别ID(数字，1=普通号, 2=专家号, 3=主任医师号),
            "registLevelReason": "挂号级别推荐理由，如：症状持续时间较短，程度较轻，建议普通号即可",
            "alternativeDepartments": ["备选科室名称1", "备选科室名称2"],
            "confidenceLevel": "high/medium/low",
            "confidenceReason": "可信度说明，如：症状描述典型，推断较确定",
            "redFlags": ["红旗征描述1", "红旗征描述2"],
            "selfCareAdvice": "自助建议，如：注意清淡饮食，3天内不缓解请就医",
            "aiAnalysis": {
                "possibleConditions": ["可能疾病1", "可能疾病2"],
                "suggestedExaminations": ["建议检查1", "建议检查2"]
            }
        }
        """;

    /**
     * 症状分析并推荐科室
     */
    public Map<String, Object> analyzeSymptoms(Map<String, Object> symptomsData) {
        log.info("AI分析症状: {}", symptomsData);

        String symptoms = (String) symptomsData.getOrDefault("symptoms", "");
        String patientName = (String) symptomsData.getOrDefault("patientName", "匿名患者");
        Integer patientAge = symptomsData.get("patientAge") != null
            ? ((Number) symptomsData.get("patientAge")).intValue()
            : null;
        String patientGender = (String) symptomsData.getOrDefault("patientGender", "");
        String sessionId = (String) symptomsData.getOrDefault("sessionId", UUID.randomUUID().toString());

        try {
            // 调用 DeepSeek AI
            String response = chatClient.prompt()
                .messages(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage("患者描述：" + symptoms)
                )
                .call()
                .content();
            log.info("AI 响应: {}", response);

            // 解析 JSON 响应
            Map<String, Object> result = parseAiResponse(response);
            result.put("sessionId", sessionId);

            // 保存导诊记录
            saveTriageRecord(patientName, patientAge, patientGender, symptoms, result);

            return result;

        } catch (Exception e) {
            log.error("AI症状分析失败", e);
            Map<String, Object> fallback = getDefaultResult();
            fallback.put("sessionId", sessionId);
            return fallback;
        }
    }

    /**
     * 解析 AI 响应为 Map
     */
    private Map<String, Object> parseAiResponse(String response) {
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonStr = extractJson(response);
            JsonNode node = objectMapper.readTree(jsonStr);

            // 紧迫等级与风险等级（兼容旧字段）
            result.put("urgencyLevel", getTextValue(node, "urgencyLevel", "IV"));
            result.put("riskLevel", mapUrgencyToRiskLevel(getTextValue(node, "urgencyLevel", "IV")));

            // 紧迫行动建议
            result.put("urgencyAdvice", getTextValue(node, "urgencyAdvice", "建议到医院就诊"));

            // 推荐科室
            result.put("recommendedDepartment", getTextValue(node, "recommendedDepartment", "内科"));
            result.put("recommendedDepartmentId", getLongValue(node, "recommendedDepartmentId", 1L));

            // 分诊依据
            result.put("departmentReason", getTextValue(node, "departmentReason", ""));

            // 推荐挂号级别
            result.put("recommendedRegistLevelId", getLongValue(node, "recommendedRegistLevelId", 1L));
            result.put("registLevelReason", getTextValue(node, "registLevelReason", ""));

            // 备选科室
            List<String> altDepts = parseStringList(node.get("alternativeDepartments"));
            result.put("alternativeDepartments", altDepts);

            // 可信度
            result.put("confidenceLevel", getTextValue(node, "confidenceLevel", "medium"));
            result.put("confidenceReason", getTextValue(node, "confidenceReason", ""));

            // 红旗征
            List<String> redFlags = parseStringList(node.get("redFlags"));
            result.put("redFlags", redFlags);

            // 推荐医生（可选）
            JsonNode doctorsNode = node.get("recommendedDoctors");
            List<Map<String, Object>> doctors = new ArrayList<>();
            if (doctorsNode != null && doctorsNode.isArray()) {
                for (JsonNode doc : doctorsNode) {
                    doctors.add(Map.of(
                        "id", getLongValue(doc, "id", 0L),
                        "name", getTextValue(doc, "name", ""),
                        "title", getTextValue(doc, "title", "")
                    ));
                }
            }
            result.put("recommendedDoctors", doctors);

            // aiAnalysis
            JsonNode analysisNode = node.get("aiAnalysis");
            Map<String, Object> aiAnalysis = new HashMap<>();
            if (analysisNode != null && analysisNode.isObject()) {
                aiAnalysis.put("possibleConditions", parseStringList(analysisNode.get("possibleConditions")));
                aiAnalysis.put("suggestedExaminations", parseStringList(analysisNode.get("suggestedExaminations")));
                aiAnalysis.put("selfCareAdvice", getTextValue(analysisNode, "selfCareAdvice", "建议就医"));
            } else {
                aiAnalysis.put("possibleConditions", List.of("待进一步检查"));
                aiAnalysis.put("suggestedExaminations", List.of("血常规"));
                aiAnalysis.put("selfCareAdvice", "建议就医");
            }
            result.put("aiAnalysis", aiAnalysis);

            // 自助建议（独立字段，与 aiAnalysis.selfCareAdvice 保持一致）
            result.put("selfCareAdvice", getTextValue(node, "selfCareAdvice",
                (String) aiAnalysis.getOrDefault("selfCareAdvice", "建议就医")));

        } catch (Exception e) {
            log.warn("解析 AI 响应失败，使用默认结果: {}", e.getMessage());
            result.putAll(getDefaultResult());
        }

        return result;
    }

    private String mapUrgencyToRiskLevel(String urgencyLevel) {
        return switch (urgencyLevel) {
            case "I" -> "critical";
            case "II" -> "urgent";
            case "III" -> "medium";
            default -> "low";
        };
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private String getTextValue(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : defaultValue;
    }

    private Long getLongValue(JsonNode node, String field, Long defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asLong() : defaultValue;
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                list.add(item.asText());
            }
        }
        return list;
    }

    /**
     * 获取降级结果（AI调用失败时）
     */
    private Map<String, Object> getDefaultResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("urgencyLevel", "IV");
        result.put("riskLevel", "low");
        result.put("urgencyAdvice", "建议到医院就诊");
        result.put("recommendedDepartment", "内科");
        result.put("recommendedDepartmentId", 1L);
        result.put("departmentReason", "");
        result.put("recommendedRegistLevelId", 1L);
        result.put("registLevelReason", "");
        result.put("alternativeDepartments", List.of());
        result.put("confidenceLevel", "medium");
        result.put("confidenceReason", "AI服务暂时不可用，建议到导诊台确认");
        result.put("redFlags", List.of());
        result.put("selfCareAdvice", "建议就医");
        result.put("recommendedDoctors", List.of());
        result.put("aiAnalysis", Map.of(
            "possibleConditions", List.of("待进一步检查"),
            "suggestedExaminations", List.of("血常规"),
            "selfCareAdvice", "建议就医"
        ));
        return result;
    }

    /**
     * 获取科室推荐
     */
    public Map<String, Object> getDepartmentRecommendation(Map<String, Object> request) {
        String department = (String) request.getOrDefault("department", "");
        Map<String, Object> result = new HashMap<>();
        result.put("department", department);
        result.put("specialists", List.of());
        result.put("waitTime", "请咨询导诊台");
        return result;
    }

    /**
     * 导诊对话
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest) {
        log.info("导诊对话请求: {}", chatRequest);

        String message = (String) chatRequest.getOrDefault("message", "");
        String sessionId = (String) chatRequest.getOrDefault("sessionId", UUID.randomUUID().toString());

        try {
            String aiResponse = chatClient.prompt()
                .messages(
                    new SystemMessage("你是一个医疗导诊助手，请根据患者的描述给出建议。回答要简洁、专业。"),
                    new UserMessage(message)
                )
                .call()
                .content();

            Map<String, Object> response = new HashMap<>();
            response.put("reply", aiResponse);
            response.put("sessionId", sessionId);
            response.put("suggestions", List.of("继续描述症状", "查看推荐科室", "结束问诊"));

            return response;

        } catch (Exception e) {
            log.error("AI对话失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("reply", "抱歉，我现在无法回答您的问题，请稍后再试。");
            response.put("sessionId", sessionId);
            response.put("suggestions", List.of("继续描述症状", "查看推荐科室", "结束问诊"));
            return response;
        }
    }

    /**
     * 保存导诊记录
     */
    private void saveTriageRecord(String patientName, Integer patientAge, String patientGender,
                                  String symptoms, Map<String, Object> result) {
        try {
            AiTriageRecord record = new AiTriageRecord();
            record.setPatientName(patientName);
            record.setPatientAge(patientAge);
            // 空字符串转为 NULL，避免违反数据库约束
            record.setPatientGender(patientGender != null && !patientGender.isBlank() ? patientGender : null);
            record.setSymptomDescription(symptoms);
            record.setRecommendDeptId(result.get("recommendedDepartmentId") != null
                ? ((Number) result.get("recommendedDepartmentId")).longValue()
                : null);
            record.setRecommendDeptName((String) result.get("recommendedDepartment"));
            record.setRiskLevel((String) result.getOrDefault("riskLevel", "normal"));
            record.setIsPriority(0);
            record.setAiAnalysis(objectMapper.writeValueAsString(result.get("aiAnalysis")));
            record.setTriageTime(LocalDateTime.now());
            record.setModelId("deepseek-chat");

            aiTriageRecordMapper.insert(record);
            log.info("导诊记录已保存: id={}", record.getId());
        } catch (Exception e) {
            log.error("保存导诊记录失败", e);
        }
    }

    /**
     * 获取导诊记录
     */
    public AiTriageRecord getTriageRecord(Long id) {
        return aiTriageRecordMapper.selectById(id);
    }

    /**
     * 获取患者的导诊记录
     */
    public List<AiTriageRecord> getPatientTriageRecords(Long patientId) {
        return aiTriageRecordMapper.selectByPatientId(patientId);
    }
}