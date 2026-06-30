package com.xikang.ai.consult.service;

import com.xikang.ai.consult.ai.memory.PreVisitChatMemoryRepository;
import com.xikang.ai.consult.ai.util.PromptUtils;
import com.xikang.ai.consult.client.TriageClient;
import com.xikang.ai.consult.client.dto.TriageSummary;
import com.xikang.ai.consult.entity.AiPreVisitRecord;
import com.xikang.ai.consult.entity.PreVisitSummary;
import com.xikang.ai.consult.mapper.AiPreVisitRecordMapper;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI 预问诊多轮对话服务
 *
 * 数据模型：每轮对话 = ai_consultation_record 一行
 *   - 同一 sessionUuid 的多行 = 一次预问诊会话
 *   - 最后一行（汇总行）填 chiefComplaint / historySummary 等结构化字段
 *
 * Spring AI 用法说明：
 *   - prompt 模板通过 @Value("classpath:...") 注入 Resource，用 PromptTemplate 渲染占位符
 *   - 对话历史通过 Spring AI 的 ChatMemoryRepository（PreVisitChatMemoryRepository）读取
 *   - 结构化总结用 ChatClient.entity() 直接转 PreVisitSummary，无需手工抠 JSON
 *   - 流式调用用 chatClient.prompt().messages(...).stream().content()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreConsultService {

    private final ChatClient chatClient;
    private final AiPreVisitRecordMapper mapper;
    private final PreVisitChatMemoryRepository chatMemoryRepository;
    private final TriageClient triageClient;

    @Value("classpath:prompts/previsit-prompt.st")
    private Resource previsitPromptResource;

    @Value("classpath:prompts/previsit-summary-prompt.st")
    private Resource summaryPromptResource;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelId;

    // ============================================================
    // 公开 API
    // ============================================================

    /**
     * 开始预问诊，返回首条 AI 提问（流式回调）
     *
     * @param registerId      挂号ID
     * @param patientId       患者ID
     * @param triageSessionId 导诊会话ID（精确关联本次导诊，避免历史污染）；未做导诊时为 null
     * @param tokenConsumer   每个 token 的回调（流式）
     * @return Map 包含 sessionUuid, done=false, finished=false
     */
    public Map<String, Object> start(Integer registerId, Integer patientId, String triageSessionId,
                                     Consumer<String> tokenConsumer) {
        String sessionUuid = UUID.randomUUID().toString();
        log.info("[预问诊开始] registerId={}, patientId={}, triageSessionId={}, sessionUuid={}",
                registerId, patientId, triageSessionId, sessionUuid);

        // 第一次 AI 提问：空历史（repository 此时还没有这个 sessionUuid 的数据）
        String patientAllergy = safeQueryPatientAllergy(patientId);
        // 拉取本次导诊上下文：优先按 sessionId 精确查（前端透传，最可靠）；
        // sessionId 缺失则按 registerId 查（靠挂号回填绑定的 register_id 兜底）；
        // 都查不到则返回 null，走完整流程。
        String triageContext = safeFetchTriageContext(triageSessionId, registerId);
        List<Message> messages = buildPromptMessages(sessionUuid, "", patientAllergy, triageContext);
        String aiReply = callAi(messages, tokenConsumer);
        boolean finished = PromptUtils.isConsultationFinished(aiReply);
        // 入库前清理 DONE 标记，保证数据库 ai_question 字段干净，回看时不污染前端
        String cleanReply = PromptUtils.cleanDoneToken(aiReply);

        // 持久化第 1 轮
        AiPreVisitRecord row = newRoundRow(registerId, patientId, sessionUuid, 1, cleanReply, null, "in_progress");
        mapper.insert(row);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionUuid", sessionUuid);
        result.put("registerId", registerId);
        result.put("roundNumber", 1);
        result.put("finished", finished);
        // 暴露导诊关联状态：triageContext 为空 = 未关联导诊（未做导诊或 sessionId 缺失），
        // 前端可据此提示"未关联导诊，将走完整问诊流程"，让降级可见、可排查。
        result.put("triageLinked", triageContext != null && !triageContext.isBlank());
        return result;
    }

    /**
     * 患者回复，AI 继续问（流式）
     */
    public Map<String, Object> reply(String sessionUuid, String patientAnswer, Consumer<String> tokenConsumer) {
        log.info("[预问诊回复] sessionUuid={}, answer={}", sessionUuid, patientAnswer);

        // 1. 加载历史
        List<AiPreVisitRecord> history = mapper.selectBySessionUuid(sessionUuid);
        if (history.isEmpty()) {
            throw new IllegalArgumentException("会话不存在: " + sessionUuid);
        }

        Integer registerId = history.get(0).getRegisterId();
        Integer patientId = history.get(0).getPatientId();
        int nextRound = mapper.selectMaxRoundNumber(sessionUuid) + 1;

        // 2. 构造 AI 调用消息：先通过 ChatMemoryRepository 读历史（此时 DB 还没写入本次 answer，
        //    所以 collectedHint 和历史 turns 都不含本次 answer），再把本次 answer 显式追加为
        //    最后一条 UserMessage。这样 AI 能正确识别"这是用户本轮新输入"。
        //    （先 buildMessages 再 insert，是为了让 prompt 结构与旧实现严格等价；
        //    insert 仍在 callAi 之前执行，保证 AI 调用失败时患者回答也不会丢失。）
        String patientAllergy = safeQueryPatientAllergy(patientId);
        // reply 阶段不再重新拉导诊上下文：导诊背景已在 start 第一轮注入到对话历史中，
        // 后续轮次通过 ChatMemory 读取历史即可感知，无需每轮重复注入（也避免 reply 还要传 sessionId 的复杂度）。
        List<Message> messages = buildPromptMessages(sessionUuid, patientAnswer, patientAllergy, null);

        // 3. 把"患者回答"持久化到第 nextRound 行
        AiPreVisitRecord answerRow = new AiPreVisitRecord();
        answerRow.setRegisterId(registerId);
        answerRow.setPatientId(patientId);
        answerRow.setSessionUuid(sessionUuid);
        answerRow.setRoundNumber(nextRound);
        answerRow.setPatientAnswer(patientAnswer);
        answerRow.setConsultationState("in_progress");
        answerRow.setCreationTime(LocalDateTime.now());
        answerRow.setUpdatedAt(LocalDateTime.now());
        answerRow.setModelId(modelId);
        mapper.insert(answerRow);

        // 4. 调用 AI 流式生成下一句提问
        String aiReply = callAi(messages, tokenConsumer);
        boolean finished = PromptUtils.isConsultationFinished(aiReply);
        // 入库前清理 DONE 标记，保证数据库 ai_question 字段干净，回看时不污染前端
        String cleanReply = PromptUtils.cleanDoneToken(aiReply);

        // 5. 写一行只有 ai_question 的记录（专门承载本轮 AI 提问）
        AiPreVisitRecord aiRow = new AiPreVisitRecord();
        aiRow.setRegisterId(registerId);
        aiRow.setPatientId(patientId);
        aiRow.setSessionUuid(sessionUuid);
        aiRow.setRoundNumber(nextRound + 1);
        aiRow.setAiQuestion(cleanReply);
        aiRow.setConsultationState("in_progress");
        aiRow.setCreationTime(LocalDateTime.now());
        aiRow.setUpdatedAt(LocalDateTime.now());
        aiRow.setModelId(modelId);
        mapper.insert(aiRow);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionUuid", sessionUuid);
        result.put("roundNumber", nextRound + 1);
        result.put("finished", finished);
        return result;
    }

    /**
     * 结束会话，调用 AI 总结，生成结构化病历
     */
    public Map<String, Object> finish(String sessionUuid) {
        log.info("[预问诊结束] sessionUuid={}", sessionUuid);

        List<AiPreVisitRecord> history = mapper.selectBySessionUuid(sessionUuid);
        if (history.isEmpty()) {
            throw new IllegalArgumentException("会话不存在: " + sessionUuid);
        }

        String conversationText = historyToText(history);
        PromptTemplate pt = new PromptTemplate(summaryPromptResource);
        String prompt = pt.render(Map.of("conversation", conversationText));

        PreVisitSummary summary;
        try {
            summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(PreVisitSummary.class);
        } catch (Exception e) {
            log.error("AI 生成结构化总结失败，使用空总结兜底", e);
            summary = emptySummary();
        }

        // 写回 session 汇总字段 + 状态
        AiPreVisitRecord update = new AiPreVisitRecord();
        update.setSessionUuid(sessionUuid);
        update.setConsultationState("completed");
        update.setCompletionTime(LocalDateTime.now());
        update.setChiefComplaint(summary.chiefComplaint());
        update.setSymptomDuration(summary.symptomDuration());
        update.setHistorySummary(summary.historySummary());
        update.setAllergySummary(summary.allergySummary());
        update.setMedicationSummary(summary.medicationSummary());
        update.setAiSummary(summary.presentIllness());
        mapper.updateSummaryBySessionUuid(update);

        // 建议检查列表序列化后单独更新（字段类型是 String）
        // 即使为空也写 "[]"，保持与旧实现一致，避免前端读到 null
        AiPreVisitRecord examUpdate = new AiPreVisitRecord();
        examUpdate.setSessionUuid(sessionUuid);
        List<String> examList = summary.suggestedExam() != null ? summary.suggestedExam() : List.of();
        try {
            examUpdate.setSuggestedExam(toJsonArray(examList));
            mapper.updateSummaryBySessionUuid(examUpdate);
        } catch (Exception e) {
            log.warn("写入 suggestedExam 失败", e);
        }

        // 返回前端用的 Map（保持旧字段名，不破坏 API 契约）
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("chiefComplaint", summary.chiefComplaint());
        summaryMap.put("symptomDuration", summary.symptomDuration());
        summaryMap.put("presentIllness", summary.presentIllness());
        summaryMap.put("historySummary", summary.historySummary());
        summaryMap.put("allergySummary", summary.allergySummary());
        summaryMap.put("medicationSummary", summary.medicationSummary());
        summaryMap.put("suggestedExam", summary.suggestedExam() != null ? summary.suggestedExam() : List.of());
        return summaryMap;
    }

    /**
     * 拉取一个挂号下的所有预问诊轮次（前端续接用）
     */
    public Map<String, Object> getSession(Integer registerId) {
        List<AiPreVisitRecord> rows = mapper.selectByRegisterId(registerId);
        if (rows.isEmpty()) {
            return Map.of("exists", false);
        }

        String sessionUuid = rows.get(0).getSessionUuid();
        String state = rows.get(0).getConsultationState();

        List<Map<String, Object>> rounds = new ArrayList<>();
        for (AiPreVisitRecord r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roundNumber", r.getRoundNumber());
            m.put("aiQuestion", r.getAiQuestion());
            m.put("patientAnswer", r.getPatientAnswer());
            m.put("modelId", r.getModelId());
            rounds.add(m);
        }

        // 如果已完成，从最后一行取汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        if ("completed".equals(state)) {
            AiPreVisitRecord last = rows.get(rows.size() - 1);
            summary.put("chiefComplaint", last.getChiefComplaint());
            summary.put("symptomDuration", last.getSymptomDuration());
            summary.put("historySummary", last.getHistorySummary());
            summary.put("allergySummary", last.getAllergySummary());
            summary.put("medicationSummary", last.getMedicationSummary());
            summary.put("aiSummary", last.getAiSummary());
            summary.put("suggestedExam", last.getSuggestedExam());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("sessionUuid", sessionUuid);
        result.put("state", state);
        result.put("rounds", rounds);
        result.put("summary", summary);
        return result;
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 调用 AI（流式），返回完整文本。
     *
     * <p>注意：{@code <<PRECONSULT_DONE>>} 标记会在流式过程中被过滤掉，**不会**推给前端；
     * 但返回的完整文本里仍然保留该标记，供 start/reply 判断 finished 状态使用。
     */
    private String callAi(List<Message> messages, Consumer<String> tokenConsumer) {
        AtomicBoolean done = new AtomicBoolean(false);
        StringBuilder full = new StringBuilder();
        // 用流式过滤器处理"标记可能跨 chunk 分片"的场景，避免前端看到 <<PRECONSULT_DONE>>
        PromptUtils.DoneTokenStreamFilter filter = new PromptUtils.DoneTokenStreamFilter();

        chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    if (chunk != null && !chunk.isEmpty() && !done.get()) {
                        full.append(chunk);
                        try {
                            String safe = filter.accept(chunk);
                            if (!safe.isEmpty()) {
                                tokenConsumer.accept(safe);
                            }
                        } catch (Exception e) {
                            log.warn("tokenConsumer 处理失败", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    done.set(true);
                    // 流结束：下发 buffer 里剩余的内容（此时不会再有后续 chunk，安全）
                    try {
                        String tail = filter.flush();
                        if (!tail.isEmpty()) {
                            tokenConsumer.accept(tail);
                        }
                    } catch (Exception e) {
                        log.warn("flush tokenConsumer 处理失败", e);
                    }
                })
                .doOnError(e -> {
                    log.error("AI 流式调用失败", e);
                    done.set(true);
                })
                .blockLast();

        return full.toString();
    }

    /**
     * 构造一次 AI 调用的完整消息序列：
     *   SystemMessage（含已采集信息 prompt） + ChatMemory 中的历史消息 + 可选的当前用户回答。
     *
     * <p>历史消息通过 {@link PreVisitChatMemoryRepository} 读取，与手写拼装解耦。
     * 当 newUserAnswer 非空时（reply 场景：本次回答尚未写入 DB，作为独立 UserMessage 追加），
     * 追加到末尾；为空（start 场景）时不追加。
     *
     * @param sessionUuid     会话 ID（conversationId）
     * @param newUserAnswer   当前轮用户回答，null/空表示不追加
     * @param patientAllergy  患者档案里的过敏史（用于预问诊时确认），null/空表示档案无过敏史
     * @param triageContext   导诊上下文（症状原文+推荐科室+AI分析），null/空表示无导诊记录
     */
    private List<Message> buildPromptMessages(String sessionUuid, String newUserAnswer,
                                              String patientAllergy, String triageContext) {
        // 1. 通过 ChatMemoryRepository 读历史消息
        List<Message> historyMessages = chatMemoryRepository.findByConversationId(sessionUuid);

        // 2. 渲染 SystemMessage：把已有消息序列作为"已采集信息"、档案过敏史、导诊上下文嵌入 prompt
        String collectedHint = messagesToText(historyMessages);
        String allergyHint = (patientAllergy == null || patientAllergy.isBlank()) ? "无" : patientAllergy;
        String triageHint = (triageContext == null || triageContext.isBlank()) ? "无" : triageContext;
        PromptTemplate pt = new PromptTemplate(previsitPromptResource);
        String systemPrompt = pt.render(Map.of(
                "collected", collectedHint,
                "patientAllergy", allergyHint,
                "triageContext", triageHint
        ));

        // 3. 组装：SystemMessage 在前，历史消息随后，最后追加新回答（如果有）
        List<Message> messages = new ArrayList<>(historyMessages.size() + 2);
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(historyMessages);
        if (newUserAnswer != null && !newUserAnswer.isBlank()) {
            messages.add(new UserMessage(newUserAnswer));
        }
        return messages;
    }

    /**
     * 安全查询患者过敏史：失败时返回 null，不阻断预问诊流程。
     */
    private String safeQueryPatientAllergy(Integer patientId) {
        if (patientId == null) {
            return null;
        }
        try {
            return mapper.selectPatientAllergy(patientId);
        } catch (Exception e) {
            log.warn("查询患者过敏史失败，patientId={}, 继续预问诊流程", patientId, e);
            return null;
        }
    }

    /**
     * 安全拉取导诊上下文并格式化为 prompt 可用的字符串。
     *
     * <p><b>查询策略（双重保障）</b>：
     * <ol>
     *   <li>优先按 {@code triageSessionId} 精确查 —— 前端透传的导诊会话ID，1:1 精确绑定本次导诊。</li>
     *   <li>sessionId 缺失时，按 {@code registerId} 查 —— 靠挂号时按 sessionId 精确回填的 register_id 兜底。</li>
     * </ol>
     * <p>只要任一渠道命中，就读到正确的本次导诊；都查不到则返回 null，预问诊降级为完整流程。
     *
     * <p><b>不会错配历史导诊</b>：sessionId 是精确匹配；registerId 也是按 sessionId 精确回填的，
     * 不再有旧的"按 patientId 猜最近一条"逻辑。
     *
     * @param triageSessionId 导诊会话 ID（优先查询键，可为 null）
     * @param registerId      挂号 ID（兜底查询键，可为 null）
     * @return 格式化后的导诊上下文文本；都查不到时返回 null
     */
    private String safeFetchTriageContext(String triageSessionId, Integer registerId) {
        // 1. 优先按 sessionId 精确查
        if (triageSessionId != null && !triageSessionId.isBlank()) {
            try {
                Result<TriageSummary> resp = triageClient.getTriageSummaryBySessionId(triageSessionId);
                log.info("[预问诊导诊上下文] 按 sessionId 命中 | sessionId={}, symptom={}",
                        triageSessionId,
                        resp != null && resp.getData() != null ? resp.getData().symptomDescription() : "null");
                if (resp != null && resp.isSuccess() && resp.getData() != null) {
                    return formatTriageContext(resp.getData());
                }
            } catch (Exception e) {
                log.warn("按 sessionId 拉取导诊摘要失败，triageSessionId={}，尝试 registerId 兜底", triageSessionId, e);
            }
        }
        // 2. sessionId 缺失或查不到，按 registerId 兜底
        if (registerId != null) {
            try {
                Result<TriageSummary> resp = triageClient.getTriageSummaryByRegisterId(registerId);
                log.info("[预问诊导诊上下文] 按 registerId 命中 | registerId={}, symptom={}",
                        registerId,
                        resp != null && resp.getData() != null ? resp.getData().symptomDescription() : "null");
                if (resp != null && resp.isSuccess() && resp.getData() != null) {
                    return formatTriageContext(resp.getData());
                }
            } catch (Exception e) {
                log.warn("按 registerId 拉取导诊摘要失败，registerId={}，预问诊降级为完整流程", registerId, e);
            }
        }
        log.info("[预问诊导诊上下文] 未命中（sessionId={}, registerId={}），预问诊走完整流程",
                triageSessionId, registerId);
        return null;
    }

    /**
     * 把 {@link TriageSummary} 格式化为注入 prompt 的文本块。
     * 各字段为空则跳过；全为空时返回 null（让上层渲染为"无"）。
     */
    private String formatTriageContext(TriageSummary t) {
        if (t == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (t.symptomDescription() != null && !t.symptomDescription().isBlank()) {
            sb.append("· 患者主诉原文：").append(t.symptomDescription()).append("\n");
        }
        if (t.recommendDeptName() != null && !t.recommendDeptName().isBlank()) {
            sb.append("· 建议就诊科室：").append(t.recommendDeptName()).append("\n");
        }
        if (t.riskLevel() != null && !t.riskLevel().isBlank()) {
            sb.append("· 紧迫度等级：").append(t.riskLevel()).append("\n");
        }
        if (t.aiAnalysisJson() != null && !t.aiAnalysisJson().isBlank()) {
            sb.append("· 导诊 AI 分析（仅供你参考，不对患者复述）：").append(t.aiAnalysisJson()).append("\n");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 把消息序列渲染成 "AI: xxx\n患者: xxx\n" 的纯文本，用于 prompt 中的"已采集信息"占位符。
     * AssistantMessage → "AI:"，UserMessage → "患者:"。
     */
    private String messagesToText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String text = PromptUtils.cleanDoneToken(m.getText());
            if (text == null || text.isBlank()) {
                continue;
            }
            String role = switch (m.getMessageType()) {
                case ASSISTANT -> "AI";
                case USER -> "患者";
                default -> null;
            };
            if (role != null) {
                sb.append(role).append(": ").append(text).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 把数据库历史行渲染为纯文本（finish 总结 prompt 用）。
     * 直接基于 DB 行，不走 ChatMemoryRepository，避免依赖消息类型映射。
     */
    private String historyToText(List<AiPreVisitRecord> history) {
        StringBuilder sb = new StringBuilder();
        for (AiPreVisitRecord r : history) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                sb.append("AI: ").append(PromptUtils.cleanDoneToken(r.getAiQuestion())).append("\n");
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                sb.append("患者: ").append(r.getPatientAnswer()).append("\n");
            }
        }
        return sb.toString();
    }

    private PreVisitSummary emptySummary() {
        return new PreVisitSummary("", "", "", "", "", "", List.of());
    }

    private String toJsonArray(List<String> items) {
        // 简单 JSON 数组序列化（避免引入额外依赖；元素本身已是纯文本）
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private AiPreVisitRecord newRoundRow(Integer registerId, Integer patientId, String sessionUuid,
                                          int roundNumber, String question, String answer, String state) {
        AiPreVisitRecord row = new AiPreVisitRecord();
        row.setRegisterId(registerId);
        row.setPatientId(patientId);
        row.setSessionUuid(sessionUuid);
        row.setRoundNumber(roundNumber);
        row.setAiQuestion(question);
        row.setPatientAnswer(answer);
        row.setConsultationState(state);
        row.setCreationTime(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setModelId(modelId);
        return row;
    }
}
