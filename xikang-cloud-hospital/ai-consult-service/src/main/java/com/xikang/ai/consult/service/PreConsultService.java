package com.xikang.ai.consult.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.consult.entity.AiPreVisitRecord;
import com.xikang.ai.consult.mapper.AiPreVisitRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreConsultService {

    private final ChatClient chatClient;
    private final AiPreVisitRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelId;

    private String previsitPromptTemplate;
    private String summaryPromptTemplate;

    private String getPrevisitPromptTemplate() {
        if (previsitPromptTemplate == null) {
            previsitPromptTemplate = loadPrompt("classpath:prompts/previsit-prompt.st");
        }
        return previsitPromptTemplate;
    }

    private String getSummaryPromptTemplate() {
        if (summaryPromptTemplate == null) {
            summaryPromptTemplate = loadPrompt("classpath:prompts/previsit-summary-prompt.st");
        }
        return summaryPromptTemplate;
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
    // 公开 API
    // ============================================================

    /**
     * 开始预问诊，返回首条 AI 提问（流式回调）
     *
     * @param registerId 挂号ID
     * @param patientId  患者ID
     * @param tokenConsumer 每个 token 的回调（流式）
     * @return Map 包含 sessionUuid, done=false, finished=false
     */
    public Map<String, Object> start(Integer registerId, Integer patientId, Consumer<String> tokenConsumer) {
        String sessionUuid = UUID.randomUUID().toString();
        log.info("[预问诊开始] registerId={}, patientId={}, sessionUuid={}", registerId, patientId, sessionUuid);

        // 第一次 AI 提问：空历史
        String aiReply = callAi(buildHistory(new ArrayList<>(), ""), tokenConsumer);

        // 持久化第 1 轮
        AiPreVisitRecord row = newRoundRow(registerId, patientId, sessionUuid, 1, aiReply, null, "in_progress");
        mapper.insert(row);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionUuid", sessionUuid);
        result.put("registerId", registerId);
        result.put("roundNumber", 1);
        result.put("finished", aiReply.contains("<<PRECONSULT_DONE>>"));
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

        // 2. 把"患者回答"写进第 nextRound 行的 patient_answer（先把 ai_question 留空，AI 响应后再回填）
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

        // 3. 构造历史并调用 AI
        String conversationText = buildConversationText(history, patientAnswer);
        String collected = buildCollectedSummary(history, patientAnswer);
        String aiReply = callAi(buildHistory(history, patientAnswer), tokenConsumer);

        // 4. 写一行只有 ai_question 的记录（专门承载本轮 AI 提问）
        AiPreVisitRecord aiRow = new AiPreVisitRecord();
        aiRow.setRegisterId(registerId);
        aiRow.setPatientId(patientId);
        aiRow.setSessionUuid(sessionUuid);
        aiRow.setRoundNumber(nextRound + 1);
        aiRow.setAiQuestion(aiReply);
        aiRow.setConsultationState("in_progress");
        aiRow.setCreationTime(LocalDateTime.now());
        aiRow.setUpdatedAt(LocalDateTime.now());
        aiRow.setModelId(modelId);
        mapper.insert(aiRow);

        boolean finished = aiReply.contains("<<PRECONSULT_DONE>>");

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
        String prompt = getSummaryPromptTemplate().replace("{conversation}", conversationText);

        String aiSummary;
        try {
            aiSummary = chatClient.prompt()
                .messages(new UserMessage(prompt))
                .call()
                .content();
        } catch (Exception e) {
            log.error("AI 生成总结失败", e);
            aiSummary = "{}";
        }

        Map<String, Object> summaryMap = parseSummary(aiSummary);

        // 把总结字段写到 session 的所有行（实际只需要写一行，但用 updateSummaryBySessionUuid
        // 保险起见更新整个会话的状态和 completion_time）
        AiPreVisitRecord update = new AiPreVisitRecord();
        update.setSessionUuid(sessionUuid);
        update.setConsultationState("completed");
        update.setCompletionTime(LocalDateTime.now());
        update.setChiefComplaint((String) summaryMap.get("chiefComplaint"));
        update.setSymptomDuration((String) summaryMap.get("symptomDuration"));
        update.setHistorySummary((String) summaryMap.get("historySummary"));
        update.setAllergySummary((String) summaryMap.get("allergySummary"));
        update.setMedicationSummary((String) summaryMap.get("medicationSummary"));
        update.setAiSummary((String) summaryMap.getOrDefault("presentIllness", ""));
        mapper.updateSummaryBySessionUuid(update);

        // 建议检查合并到 suggestedExam 字段
        Object suggested = summaryMap.get("suggestedExam");
        if (suggested instanceof List<?> list) {
            try {
                AiPreVisitRecord examUpdate = new AiPreVisitRecord();
                examUpdate.setSessionUuid(sessionUuid);
                examUpdate.setSuggestedExam(objectMapper.writeValueAsString(list));
                mapper.updateSummaryBySessionUuid(examUpdate);
            } catch (Exception ignored) {}
        }

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

        // 取该 registerId 的所有行，按 round_number 升序
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
     * 调用 AI（流式），返回完整文本
     */
    private String callAi(List<Message> messages, Consumer<String> tokenConsumer) {
        AtomicBoolean done = new AtomicBoolean(false);
        StringBuilder full = new StringBuilder();

        chatClient.prompt()
            .messages(messages)
            .stream()
            .content()
            .doOnNext(chunk -> {
                if (chunk != null && !chunk.isEmpty() && !done.get()) {
                    full.append(chunk);
                    try {
                        tokenConsumer.accept(chunk);
                    } catch (Exception e) {
                        log.warn("tokenConsumer 处理失败", e);
                    }
                }
            })
            .doOnComplete(() -> done.set(true))
            .doOnError(e -> {
                log.error("AI 流式调用失败", e);
                done.set(true);
            })
            .blockLast();

        return full.toString();
    }

    private List<Message> buildHistory(List<AiPreVisitRecord> history, String newUserAnswer) {
        List<Message> messages = new ArrayList<>();
        String collectedHint = collectedFromHistory(history);
        String systemPrompt = getPrevisitPromptTemplate().replace("{collected}", collectedHint);
        messages.add(new SystemMessage(systemPrompt));

        for (AiPreVisitRecord r : history) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                messages.add(new AssistantMessage(cleanDoneToken(r.getAiQuestion())));
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                messages.add(new UserMessage(r.getPatientAnswer()));
            }
        }
        if (newUserAnswer != null && !newUserAnswer.isBlank()) {
            messages.add(new UserMessage(newUserAnswer));
        }
        return messages;
    }

    private String buildConversationText(List<AiPreVisitRecord> history, String latestAnswer) {
        StringBuilder sb = new StringBuilder();
        for (AiPreVisitRecord r : history) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                sb.append("AI: ").append(cleanDoneToken(r.getAiQuestion())).append("\n");
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                sb.append("患者: ").append(r.getPatientAnswer()).append("\n");
            }
        }
        if (latestAnswer != null && !latestAnswer.isBlank()) {
            sb.append("患者: ").append(latestAnswer).append("\n");
        }
        return sb.toString();
    }

    private String buildCollectedSummary(List<AiPreVisitRecord> history, String latestAnswer) {
        return collectedFromHistory(history)
            + (latestAnswer != null && !latestAnswer.isBlank() ? "\n患者最近说：" + latestAnswer : "");
    }

    private String collectedFromHistory(List<AiPreVisitRecord> history) {
        StringBuilder sb = new StringBuilder();
        for (AiPreVisitRecord r : history) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                sb.append("AI: ").append(cleanDoneToken(r.getAiQuestion())).append("\n");
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                sb.append("患者: ").append(r.getPatientAnswer()).append("\n");
            }
        }
        return sb.toString();
    }

    private String historyToText(List<AiPreVisitRecord> history) {
        StringBuilder sb = new StringBuilder();
        for (AiPreVisitRecord r : history) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                sb.append("AI: ").append(cleanDoneToken(r.getAiQuestion())).append("\n");
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                sb.append("患者: ").append(r.getPatientAnswer()).append("\n");
            }
        }
        return sb.toString();
    }

    private String cleanDoneToken(String text) {
        return text == null ? "" : text.replace("<<PRECONSULT_DONE>>", "").trim();
    }

    private Map<String, Object> parseSummary(String aiText) {
        Map<String, Object> result = new HashMap<>();
        try {
            String json = extractJson(aiText);
            JsonNode node = objectMapper.readTree(json);
            result.put("chiefComplaint", textOrEmpty(node, "chiefComplaint"));
            result.put("symptomDuration", textOrEmpty(node, "symptomDuration"));
            result.put("presentIllness", textOrEmpty(node, "presentIllness"));
            result.put("historySummary", textOrEmpty(node, "historySummary"));
            result.put("allergySummary", textOrEmpty(node, "allergySummary"));
            result.put("medicationSummary", textOrEmpty(node, "medicationSummary"));
            JsonNode exam = node.get("suggestedExam");
            List<String> exams = new ArrayList<>();
            if (exam != null && exam.isArray()) {
                exam.forEach(n -> exams.add(n.asText()));
            }
            result.put("suggestedExam", exams);
        } catch (Exception e) {
            log.warn("解析 AI 总结失败: {}", e.getMessage());
        }
        return result;
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? "" : n.asText("");
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
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
