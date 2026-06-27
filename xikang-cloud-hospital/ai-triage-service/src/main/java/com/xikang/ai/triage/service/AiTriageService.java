package com.xikang.ai.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.triage.dto.TriageRequest;
import com.xikang.ai.triage.dto.TriageResult;
import com.xikang.ai.triage.dto.TriageSummary;
import com.xikang.ai.triage.entity.AiTriageRecord;
import com.xikang.ai.triage.mapper.AiTriageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Triage Service - AI导诊服务
 *
 * <p>Spring AI 用法说明：
 * <ul>
 *   <li>prompt 模板通过 {@code @Value("classpath:...")} 注入 {@link Resource}，用 {@link PromptTemplate} 渲染</li>
 *   <li>结构化结果用 {@code chatClient.prompt().user(...).call().entity(TriageResult.class)}，
 *       JSON schema 由 Spring AI 内部通过 BeanOutputConverter 自动注入到 prompt 末尾</li>
 *   <li>多轮对话（chat）采用标准链式 API {@code .system(...).user(...).call().content()}</li>
 *   <li>异常时返回 {@link #fallbackResult(String)} 降级结果，保证服务可用</li>
 * </ul>
 *
 * <p>"导诊 → 预问诊"上下文串联：{@link #getTriageSummary(Integer)} 暴露精简导诊小结，
 * 下游服务（ai-consult-service）可按 registerId 反查并注入到自己的 prompt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTriageService {

    private final ChatClient chatClient;
    private final AiTriageRecordMapper aiTriageRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * RAG 向量库。可选注入：RAG 关闭时这个 Bean 不存在，字段为 null。
     * 用 setter 注入是因为 Spring 对 Optional 构造器注入支持不完美。
     */
    @Autowired(required = false)
    private VectorStore vectorStore;

    @Value("classpath:prompts/triage-prompt.st")
    private Resource triagePromptResource;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelId;

    /**
     * 症状分析并推荐科室（结构化输出）
     *
     * @param request 入参 DTO
     * @return 兼容旧前端的 Map 响应（字段名保持不变）
     */
    public Map<String, Object> analyzeSymptoms(TriageRequest request) {
        log.info("AI分析症状: {}", request);
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString() : request.sessionId();

        TriageResult result;
        try {
            result = callTriageModel(request);
            log.info("[triage] AI 调用成功：dept={} (id={}), urgency={}, confidence={}",
                    result.recommendedDepartment(), result.recommendedDepartmentId(),
                    result.urgencyLevel(), result.confidenceLevel());
        } catch (Exception e) {
            log.error("[triage] AI 调用失败，使用降级结果", e);
            result = fallbackResult("AI服务暂时不可用，建议到导诊台确认");
        }

        // 组装兼容旧前端的 Map 响应
        Map<String, Object> body = toResponseBody(result);
        body.put("sessionId", sessionId);

        // 保存导诊记录
        saveTriageRecord(request, result);

        return body;
    }

    /**
     * 调用模型，返回结构化结果。
     *
     * <p>JSON schema 由 {@code .entity(TriageResult.class)} 内部通过
     * {@link BeanOutputConverter} 自动追加到 user prompt 末尾，
     * 因此 prompt 模板里不需要手动写 {@code {format}} 占位符（避免双重注入）。
     *
     * <p><b>RAG 检索</b>：手动调用 vectorStore.similaritySearch()，
     * 把检索结果渲染进 prompt 模板的 {ragContext} 占位符。
     * 不再用 Spring AI 1.0 的 QuestionAnswerAdvisor，因为它的 promptTemplate
     * 用渲染结果替换 user message 时不会追加用户原始问题，
     * 导致症状文本被抹掉、模型输出全空字段（已知 bug 行为）。
     */
    private TriageResult callTriageModel(TriageRequest request) {
        String symptoms = request.symptoms() == null ? "" : request.symptoms();
        String ragContext = retrieveRagContext(symptoms);

        PromptTemplate pt = new PromptTemplate(triagePromptResource);
        String prompt = pt.render(Map.of(
                "patientInfo", formatPatientInfo(request),
                "symptoms", symptoms,
                "ragContext", ragContext
        ));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(TriageResult.class);
    }

    /**
     * 手动执行 RAG 检索，把命中文档拼成 ragContext 字符串。
     *
     * <p>命中数 = 0 时返回"无相关知识库命中"提示，避免模型在缺少证据时胡乱生成。
     * 这样症状和检索结果都在同一个 user message 里，模型一定能看见症状。
     */
    private String retrieveRagContext(String symptoms) {
        if (vectorStore == null || symptoms.isBlank()) {
            return "（知识库未启用或无输入症状，跳过检索）";
        }
        try {
            // topK 从 5 降到 3：之前 topK=5 + prompt 2000+ 字符时偶发 Connection reset，
                // 怀疑阿里云专属实例对请求体大小有限制；topK=3 让 prompt 更紧凑。
                List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(symptoms)
                    .topK(3)
                    .similarityThreshold(0.5)
                    .build());
            if (docs.isEmpty()) {
                return "（知识库中未检索到与症状相关的条目，请基于通用医学知识回答）";
            }
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (Document d : docs) {
                sb.append("[").append(idx++).append("] ").append(d.getFormattedContent()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[triage] RAG 检索失败，继续无 RAG 推理: {}", e.getMessage());
            return "（知识库检索异常，本次回答不依赖知识库）";
        }
    }

    /**
     * 拼装"患者信息"段，喂给 prompt 占位符。
     */
    private String formatPatientInfo(TriageRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("姓名：").append(request.patientName() == null || request.patientName().isBlank()
                ? "匿名患者" : request.patientName());
        if (request.patientAge() != null) {
            sb.append("；年龄：").append(request.patientAge()).append("岁");
        }
        if (request.patientGender() != null && !request.patientGender().isBlank()) {
            sb.append("；性别：").append(request.patientGender());
        }
        return sb.toString();
    }

    /**
     * 把结构化结果转为兼容旧前端的 Map。
     * 字段命名与改造前的 parseAiResponse 输出完全一致。
     */
    /**
     * 把"脏"或缺失的科室 ID 规整：null 或 0 都视为无效，返回 null。
     *
     * <p>为什么 0 也要视为无效：
     * AI 通过 BeanOutputConverter 反序列化 TriageResult 时，JSON 缺失字段会得到 {@code Long=0}。
     * 0 不在 department 表里，写库会撞外键 fk_ai_triage_dept → DataIntegrityViolationException。
     * 之前代码 {@code r.recommendedDepartmentId() != null ? r.recommendedDepartmentId() : 1L}
     * 用 1L 兜底，导致缺数据时被错配成"内科"。修复：兜底改为 null，让上层按"待确认"处理。
     */
    private Long sanitizeDeptId(Long id) {
        if (id == null || id <= 0L) return null;
        return id;
    }

    private Map<String, Object> toResponseBody(TriageResult r) {
        Map<String, Object> body = new LinkedHashMap<>();

        // 紧迫等级 + 旧字段 riskLevel
        String urgency = r.urgencyLevel() == null || r.urgencyLevel().isBlank() ? "IV" : r.urgencyLevel();
        body.put("urgencyLevel", urgency);
        body.put("riskLevel", mapUrgencyToRiskLevelForApi(urgency));

        // 领域护栏：用户输入与医疗无关时透传到前端，触发友好引导卡片
        boolean outOfScope = Boolean.TRUE.equals(r.isOutOfScope());
        body.put("isOutOfScope", outOfScope);
        body.put("outOfScopeMessage", outOfScope
                ? nullSafe(r.outOfScopeMessage(), "我是医疗分诊助手，请告诉我您的症状，我来帮您推荐合适的科室。")
                : null);

        body.put("urgencyAdvice", nullSafe(r.urgencyAdvice(), "建议到医院就诊"));
        body.put("recommendedDepartment", nullSafe(r.recommendedDepartment(), "内科"));
        body.put("recommendedDepartmentId", sanitizeDeptId(r.recommendedDepartmentId()));
        body.put("departmentReason", nullSafe(r.departmentReason(), ""));
        body.put("recommendedRegistLevelId", sanitizeDeptId(r.recommendedRegistLevelId()));
        body.put("registLevelReason", nullSafe(r.registLevelReason(), ""));
        body.put("alternativeDepartments", r.alternativeDepartments() != null ? r.alternativeDepartments() : List.of());
        body.put("confidenceLevel", nullSafe(r.confidenceLevel(), "medium"));
        body.put("confidenceReason", nullSafe(r.confidenceReason(), ""));
        body.put("redFlags", r.redFlags() != null ? r.redFlags() : List.of());
        body.put("selfCareAdvice", nullSafe(r.selfCareAdvice(), "建议就医"));
        body.put("recommendedDoctors", List.of()); // 当前不推荐具体医生

        // aiAnalysis 子结构（保持旧字段）
        Map<String, Object> aiAnalysis = new LinkedHashMap<>();
        TriageResult.AiAnalysis a = r.aiAnalysis();
        if (a != null) {
            aiAnalysis.put("possibleConditions", a.possibleConditions() != null ? a.possibleConditions() : List.of("待进一步检查"));
            aiAnalysis.put("suggestedExaminations", a.suggestedExaminations() != null ? a.suggestedExaminations() : List.of("血常规"));
            aiAnalysis.put("selfCareAdvice", nullSafe(r.selfCareAdvice(), "建议就医"));
        } else {
            aiAnalysis.put("possibleConditions", List.of("待进一步检查"));
            aiAnalysis.put("suggestedExaminations", List.of("血常规"));
            aiAnalysis.put("selfCareAdvice", "建议就医");
        }
        body.put("aiAnalysis", aiAnalysis);

        return body;
    }

    /**
     * 紧迫等级 → 旧版 riskLevel（normal/urgent/critical/medium/low）
     */
    /**
     * 把 urgencyLevel（I/II/III/IV）映射成 risk_level。
     *
     * <p><b>必须与 {@code ai_triage_record.chk_ai_triage_risk} CHECK 约束对齐</b>：
     * <pre>CHECK (risk_level IN ('normal', 'urgent', 'critical'))</pre>
     * 之前版本误用 'low'/'medium'，会违反约束导致保存失败。
     *
     * <p>映射规则：
     * <ul>
     *   <li>I   → critical（立即就医，危急）</li>
     *   <li>II  → urgent（尽快就医，紧急）</li>
     *   <li>III → normal（建议就医，普通）</li>
     *   <li>IV/其他 → normal（常规就诊，最低档也归 normal）</li>
     * </ul>
     */
    private String mapUrgencyToRiskLevelForDb(String urgencyLevel) {
        return switch (urgencyLevel) {
            case "I" -> "critical";
            case "II" -> "urgent";
            default -> "normal";   // III / IV / null 都归 normal
        };
    }

    /**
     * 把 urgencyLevel（I/II/III/IV）映射成 risk_level —— 用于返回给前端的字段。
     *
     * <p>保持 low/medium/high 三档语义（与旧前端契约一致），不影响前端展示。
     * 注意：这个值<b>不能</b>直接写入 ai_triage_record.risk_level（会违反 CHECK 约束），
     * 写库必须用 {@link #mapUrgencyToRiskLevelForDb}。
     */
    private String mapUrgencyToRiskLevelForApi(String urgencyLevel) {
        return switch (urgencyLevel) {
            case "I" -> "high";
            case "II" -> "high";
            case "III" -> "medium";
            default -> "low";
        };
    }

    private String nullSafe(String v, String defaultValue) {
        return v == null || v.isBlank() ? defaultValue : v;
    }

    /**
     * 降级结果（AI 调用失败时）
     */
    private TriageResult fallbackResult(String confidenceReason) {
        return new TriageResult(
                "IV",                                    // urgencyLevel
                "建议到医院就诊",                         // urgencyAdvice
                "内科",                                  // recommendedDepartment
                1L,                                      // recommendedDepartmentId
                "",                                      // departmentReason
                1L,                                      // recommendedRegistLevelId
                "",                                      // registLevelReason
                List.of(),                               // alternativeDepartments
                "medium",                                // confidenceLevel
                confidenceReason,                        // confidenceReason
                List.of(),                               // redFlags
                "建议就医",                               // selfCareAdvice
                new TriageResult.AiAnalysis(
                        List.of("待进一步检查"),
                        List.of("血常规")
                ),
                false,                                   // isOutOfScope
                null                                     // outOfScopeMessage
        );
    }

    /**
     * 获取科室推荐（轻量占位实现，前端常配合 analyze 使用）
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
     * 导诊对话（chat 接口）。
     *
     * <p>采用 Spring AI 标准链式 API {@code .system(...).user(...).call().content()}，
     * 不再手动构造 SystemMessage/UserMessage。当前为单轮无状态实现；
     * 如需跨会话记忆，可在 ChatClient 上挂 PromptChatMemoryAdvisor
     * + 自定义 ChatMemoryRepository（参考 ai-consult-service 的 PreVisitChatMemoryRepository）。
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest) {
        log.info("导诊对话请求: {}", chatRequest);

        String message = (String) chatRequest.getOrDefault("message", "");
        String sessionId = (String) chatRequest.getOrDefault("sessionId", UUID.randomUUID().toString());

        try {
            String aiResponse = chatClient.prompt()
                    .system("你是一个医疗导诊助手，请根据患者的描述给出建议。回答要简洁、专业。")
                    .user(message)
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
     * 保存导诊记录到数据库。
     */
    private void saveTriageRecord(TriageRequest request, TriageResult result) {
        try {
            AiTriageRecord record = new AiTriageRecord();
            record.setPatientName(request.patientName() == null || request.patientName().isBlank()
                    ? "匿名患者" : request.patientName());
            record.setPatientAge(request.patientAge());
            // 空字符串转 NULL，避免违反 CHECK 约束（patient_gender 只允许 '男'/'女'/NULL）
            record.setPatientGender(request.patientGender() != null && !request.patientGender().isBlank()
                    ? request.patientGender() : null);
            record.setSymptomDescription(request.symptoms() == null ? "" : request.symptoms());

            // 防御性过滤 0/负数：BeanOutputConverter 把缺失字段填成 Long=0，
            // 0 不在 department 表里，会撞 fk_ai_triage_dept 外键。规整为 null 让列保持 NULL。
            Long deptId = sanitizeDeptId(result.recommendedDepartmentId());
            if (deptId != null) {
                record.setRecommendDeptId(deptId);
            }
            // 推荐科室名也做兜底：AI 偶尔不填 name，不写入空串，避免后续排查困惑
            String deptName = nullSafe(result.recommendedDepartment(), null);
            if (deptName != null) {
                record.setRecommendDeptName(deptName);
            }
            record.setRiskLevel(mapUrgencyToRiskLevelForDb(nullSafe(result.urgencyLevel(), "IV")));
            record.setIsPriority(0);
            // aiAnalysis 为 null 时不序列化空对象，避免污染展示
            if (result.aiAnalysis() != null) {
                record.setAiAnalysis(objectMapper.writeValueAsString(result.aiAnalysis()));
            }
            record.setRegisterId(request.registerId() != null ? request.registerId().longValue() : null);
            record.setTriageTime(LocalDateTime.now());
            record.setModelId(modelId);

            aiTriageRecordMapper.insert(record);
            log.info("导诊记录已保存: id={}, registerId={}, deptId={}, deptName={}",
                    record.getId(), request.registerId(), deptId, deptName);
        } catch (Exception e) {
            log.error("保存导诊记录失败", e);
        }
    }

    /**
     * 获取导诊记录。
     */
    public AiTriageRecord getTriageRecord(Long id) {
        return aiTriageRecordMapper.selectById(id);
    }

    /**
     * 获取患者的导诊记录。
     */
    public List<AiTriageRecord> getPatientTriageRecords(Long patientId) {
        return aiTriageRecordMapper.selectByPatientId(patientId);
    }

    // ============================================================
    // 导诊 → 预问诊 上下文串联钩子
    // ============================================================

    /**
     * 按 registerId 反查导诊小结，供下游服务（如预问诊）注入到自己的 prompt 上下文。
     *
     * <p>这是"导诊 → 预问诊"记忆串联的对外契约：预问诊开始时调用本方法，
     * 把导诊阶段已采集到的症状、推荐科室、紧迫度等喂给预问诊模型，避免患者重复描述病情。
     *
     * @param registerId 挂号 ID
     * @return 导诊小结；若该挂号未做过导诊，返回 null
     */
    public TriageSummary getTriageSummary(Integer registerId) {
        if (registerId == null) {
            return null;
        }
        AiTriageRecord r = aiTriageRecordMapper.selectByRegisterId(registerId);
        if (r == null) {
            return null;
        }
        return new TriageSummary(
                r.getSymptomDescription(),
                r.getRecommendDeptName(),
                r.getRecommendDeptId(),
                r.getRiskLevel(),
                r.getAiAnalysis()
        );
    }
}
