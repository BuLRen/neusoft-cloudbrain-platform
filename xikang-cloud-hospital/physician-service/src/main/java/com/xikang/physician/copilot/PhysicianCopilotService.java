package com.xikang.physician.copilot;

import com.xikang.physician.ai.DifyAgentChatResult;
import com.xikang.physician.ai.DifyAgentClient;
import com.xikang.physician.ai.DifyWorkflowException;
import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.context.PhysicianAuthContext;
import com.xikang.physician.copilot.entity.PhysicianAiChatMessage;
import com.xikang.physician.copilot.entity.PhysicianAiChatSession;
import com.xikang.physician.copilot.mapper.PhysicianAiChatMessageMapper;
import com.xikang.physician.copilot.mapper.PhysicianAiChatSessionMapper;
import com.xikang.physician.service.PhysicianService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Service
public class PhysicianCopilotService {

    private static final int HISTORY_LIMIT = 40;
    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
        "trigger_preliminary_diagnosis",
        "trigger_w2",
        "trigger_w3",
        "trigger_w4",
        "trigger_w5"
    );

    private final ChatClient copilotChatClient;
    private final DifyAgentClient difyAgentClient;
    private final PhysicianCopilotContextBuilder contextBuilder;
    private final PhysicianAiChatMessageMapper chatMessageMapper;
    private final PhysicianAiChatSessionMapper chatSessionMapper;
    private final PhysicianService physicianService;
    private final PhysicianAiPipelineService pipelineService;

    public PhysicianCopilotService(
        @Qualifier("physicianCopilotChatClient") ChatClient copilotChatClient,
        DifyAgentClient difyAgentClient,
        PhysicianCopilotContextBuilder contextBuilder,
        PhysicianAiChatMessageMapper chatMessageMapper,
        PhysicianAiChatSessionMapper chatSessionMapper,
        PhysicianService physicianService,
        PhysicianAiPipelineService pipelineService
    ) {
        this.copilotChatClient = copilotChatClient;
        this.difyAgentClient = difyAgentClient;
        this.contextBuilder = contextBuilder;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.physicianService = physicianService;
        this.pipelineService = pipelineService;
    }

    public List<Map<String, Object>> listSessions(Long registerId) {
        assertPatientAccess(registerId);
        return chatSessionMapper.selectByRegisterId(registerId).stream()
            .map(this::sessionToDto)
            .toList();
    }

    @Transactional
    public Map<String, Object> createSession(Long registerId, String title) {
        assertPatientAccess(registerId);
        String resolvedTitle = title == null || title.isBlank() ? DEFAULT_SESSION_TITLE : title.trim();
        if (resolvedTitle.length() > 128) {
            resolvedTitle = resolvedTitle.substring(0, 128);
        }

        PhysicianAiChatSession session = new PhysicianAiChatSession();
        session.setRegisterId(registerId);
        session.setDoctorId(PhysicianAuthContext.employeeIdOrNull());
        session.setTitle(resolvedTitle);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        return sessionToDto(session);
    }

    @Transactional
    public void deleteSession(Long registerId, Long sessionId) {
        assertSessionAccess(registerId, sessionId);
        chatMessageMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteById(sessionId);
    }

    public void renameSession(Long registerId, Long sessionId, String title) {
        assertSessionAccess(registerId, sessionId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        String resolvedTitle = title.trim();
        if (resolvedTitle.length() > 128) {
            resolvedTitle = resolvedTitle.substring(0, 128);
        }
        chatSessionMapper.updateTitle(sessionId, resolvedTitle);
    }

    public List<Map<String, Object>> getHistory(Long registerId, Long sessionId) {
        assertSessionAccess(registerId, sessionId);
        return chatMessageMapper.selectBySession(registerId, sessionId, HISTORY_LIMIT).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public void clearHistory(Long registerId, Long sessionId) {
        assertSessionAccess(registerId, sessionId);
        chatMessageMapper.deleteBySession(registerId, sessionId);
        chatSessionMapper.clearDifyConversationId(sessionId);
    }

    public Map<String, Object> chat(
        Long registerId,
        Long sessionId,
        String userMessage,
        Consumer<String> tokenConsumer,
        Consumer<Map<String, Object>> thoughtConsumer
    ) {
        assertSessionAccess(registerId, sessionId);
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }

        String trimmed = userMessage.trim();
        saveMessage(registerId, sessionId, "user", trimmed, null);
        chatSessionMapper.touchUpdatedAt(sessionId);

        if (difyAgentClient.isEnabled()) {
            return chatViaDifyAgent(registerId, sessionId, trimmed, tokenConsumer, thoughtConsumer);
        }
        return chatViaSpringAi(registerId, sessionId, trimmed, tokenConsumer);
    }

    private Map<String, Object> chatViaDifyAgent(
        Long registerId,
        Long sessionId,
        String userMessage,
        Consumer<String> tokenConsumer,
        Consumer<Map<String, Object>> thoughtConsumer
    ) {
        PhysicianAiChatSession session = chatSessionMapper.selectById(sessionId);
        Map<String, String> inputs = contextBuilder.buildAgentInputs(registerId);
        String user = buildDifyUser(registerId);

        DifyAgentChatResult result = difyAgentClient.streamChat(
            inputs,
            userMessage,
            user,
            session.getDifyConversationId(),
            tokenConsumer,
            thoughtConsumer
        );

        String response = result.getAnswer();
        if (response == null || response.isBlank()) {
            response = "抱歉，我暂时无法生成有效回答，请稍后重试。";
            if (tokenConsumer != null) {
                tokenConsumer.accept(response);
            }
        }

        if (result.getConversationId() != null && !result.getConversationId().isBlank()) {
            chatSessionMapper.updateDifyConversationId(sessionId, result.getConversationId());
        }

        String toolCallsJson = result.getThoughts().isEmpty()
            ? null
            : contextBuilder.toJsonSafe(result.getThoughts());
        saveMessage(registerId, sessionId, "assistant", response, toolCallsJson);
        chatSessionMapper.touchUpdatedAt(sessionId);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("registerId", registerId);
        meta.put("sessionId", sessionId);
        meta.put("engine", "dify-agent");
        meta.put("difyConversationId", result.getConversationId());
        meta.put("done", true);
        return meta;
    }

    private Map<String, Object> chatViaSpringAi(
        Long registerId,
        Long sessionId,
        String trimmed,
        Consumer<String> tokenConsumer
    ) {
        String systemPrompt = contextBuilder.build(registerId);
        List<Message> historyMessages = loadHistoryMessages(registerId, sessionId);

        PhysicianCopilotTools.bindRegisterId(registerId);
        try {
            String response = copilotChatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(trimmed)
                .call()
                .content();

            if (response == null || response.isBlank()) {
                response = "抱歉，我暂时无法生成有效回答，请稍后重试。";
            }

            streamText(response, tokenConsumer);
            List<Map<String, Object>> toolCalls = PhysicianCopilotTools.drainToolCalls();
            String toolCallsJson = toolCalls.isEmpty() ? null : contextBuilder.toJsonSafe(toolCalls);
            saveMessage(registerId, sessionId, "assistant", response, toolCallsJson);
            chatSessionMapper.touchUpdatedAt(sessionId);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("registerId", registerId);
            meta.put("sessionId", sessionId);
            meta.put("engine", "spring-ai");
            meta.put("done", true);
            return meta;
        } catch (Exception ex) {
            log.error("Copilot chat failed registerId={} sessionId={}", registerId, sessionId, ex);
            throw ex;
        } finally {
            PhysicianCopilotTools.clearRegisterId();
        }
    }

    private String buildDifyUser(Long registerId) {
        Long doctorId = PhysicianAuthContext.employeeIdOrNull();
        if (doctorId != null) {
            return "doctor-" + doctorId + "-reg-" + registerId;
        }
        return "physician-reg-" + registerId;
    }

    public Map<String, Object> runCopilotAction(Long registerId, String actionType) {
        assertPatientAccess(registerId);
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType 不能为空");
        }
        String normalized = actionType.trim();
        if (!ALLOWED_ACTION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的操作类型: " + normalized);
        }

        try {
            Map<String, Object> result = switch (normalized) {
                case "trigger_preliminary_diagnosis" -> runPreliminaryDiagnosisAction(registerId);
                case "trigger_w2" -> pipelineService.runW2(registerId);
                case "trigger_w3" -> pipelineService.runW3(registerId);
                case "trigger_w4" -> pipelineService.runW4(registerId);
                case "trigger_w5" -> pipelineService.runW5(registerId);
                default -> throw new IllegalArgumentException("不支持的操作类型: " + normalized);
            };

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("actionType", normalized);
            response.put("registerId", registerId);
            response.put("success", true);
            response.put("summary", buildActionSummary(normalized, result));
            response.put("data", result);
            return response;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Copilot action failed registerId={} actionType={}", registerId, normalized, ex);
            throw new IllegalStateException("操作执行失败，请稍后重试");
        }
    }

    private Map<String, Object> runPreliminaryDiagnosisAction(Long registerId) {
        Map<String, Object> record = physicianService.getMedicalRecord(registerId);
        Map<String, Object> patient = physicianService.getPatient(registerId);
        String text = buildPreliminaryInputText(record, patient);
        if (text.isBlank()) {
            throw new IllegalArgumentException("暂无病历或预问诊内容，无法运行初步诊断");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("registerId", registerId);
        request.put("text", text);
        request.put("preHandle", true);
        return pipelineService.runPreliminaryDiagnosis(request);
    }

    private String buildPreliminaryInputText(Map<String, Object> record, Map<String, Object> patient) {
        if (record != null && !record.isEmpty()) {
            String recordText = buildRecordText(record);
            if (!recordText.isBlank()) {
                return recordText;
            }
        }
        return buildPreConsultationText(patient);
    }

    private String buildRecordText(Map<String, Object> record) {
        List<String> parts = new ArrayList<>();
        appendLine(parts, "主诉", record.get("readme"));
        appendLine(parts, "现病史", record.get("present"));
        appendLine(parts, "现病治疗", record.get("presentTreat"));
        appendLine(parts, "既往史", record.get("history"));
        appendLine(parts, "过敏史", record.get("allergy"));
        appendLine(parts, "体格检查", record.get("physique"));
        return String.join("\n", parts);
    }

    @SuppressWarnings("unchecked")
    private String buildPreConsultationText(Map<String, Object> patient) {
        if (patient == null) {
            return "";
        }
        Object summaryObj = patient.get("aiConsultSummary");
        if (!(summaryObj instanceof Map<?, ?> summary)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        appendLine(parts, "主诉", ((Map<String, Object>) summary).get("chiefComplaint"));
        appendLine(parts, "摘要", ((Map<String, Object>) summary).get("aiSummary"));
        return String.join("\n", parts);
    }

    private void appendLine(List<String> parts, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            parts.add(label + "：" + text);
        }
    }

    private String buildActionSummary(String actionType, Map<String, Object> result) {
        return switch (actionType) {
            case "trigger_preliminary_diagnosis" -> textOrDefault(
                result.get("primaryDiagnosis"),
                "初步诊断工作流已完成"
            );
            case "trigger_w2" -> {
                Object assessment = result.get("preliminaryAssessment");
                int count = listSize(result.get("recommendedExaminations"));
                yield assessment != null && !String.valueOf(assessment).isBlank()
                    ? String.valueOf(assessment)
                    : "W2 检查推荐完成，共推荐 " + count + " 项";
            }
            case "trigger_w3" -> textOrDefault(
                result.get("clinicalImpression"),
                "W3 结果解读已完成"
            );
            case "trigger_w4" -> {
                int count = listSize(result.get("suggestions"));
                yield "W4 门诊确诊推理完成，共 " + count + " 条诊断建议";
            }
            case "trigger_w5" -> {
                int count = listSize(result.get("suggestions"));
                yield "W5 智能荐药完成，共 " + count + " 条荐药建议";
            }
            default -> "操作已完成";
        };
    }

    private String textOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private void assertPatientAccess(Long registerId) {
        if (registerId == null || registerId <= 0) {
            throw new IllegalArgumentException("registerId 无效");
        }
        Map<String, Object> patient = physicianService.getPatient(registerId);
        if (patient == null) {
            throw new IllegalArgumentException("患者不存在或无权访问");
        }
    }

    private void assertSessionAccess(Long registerId, Long sessionId) {
        assertPatientAccess(registerId);
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 无效");
        }
        PhysicianAiChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !registerId.equals(session.getRegisterId())) {
            throw new IllegalArgumentException("对话不存在或无权访问");
        }
    }

    private List<Message> loadHistoryMessages(Long registerId, Long sessionId) {
        List<PhysicianAiChatMessage> rows = chatMessageMapper.selectBySession(registerId, sessionId, HISTORY_LIMIT);
        List<Message> messages = new ArrayList<>(rows.size());
        for (PhysicianAiChatMessage row : rows) {
            if ("user".equals(row.getRole())) {
                messages.add(new UserMessage(row.getContent()));
            } else if ("assistant".equals(row.getRole())) {
                messages.add(new AssistantMessage(row.getContent()));
            }
        }
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
            messages.remove(messages.size() - 1);
        }
        return messages;
    }

    private void saveMessage(Long registerId, Long sessionId, String role, String content, String toolCallsJson) {
        PhysicianAiChatMessage row = new PhysicianAiChatMessage();
        row.setRegisterId(registerId);
        row.setSessionId(sessionId);
        row.setDoctorId(PhysicianAuthContext.employeeIdOrNull());
        row.setRole(role);
        row.setContent(content);
        row.setToolCallsJson(toolCallsJson);
        row.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(row);
    }

    private void streamText(String text, Consumer<String> tokenConsumer) {
        if (tokenConsumer == null) {
            return;
        }
        final int chunkSize = 12;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(text.length(), i + chunkSize);
            tokenConsumer.accept(text.substring(i, end));
        }
    }

    private Map<String, Object> toDto(PhysicianAiChatMessage row) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", row.getId());
        dto.put("registerId", row.getRegisterId());
        dto.put("sessionId", row.getSessionId());
        dto.put("role", row.getRole());
        dto.put("content", row.getContent());
        dto.put("toolCallsJson", row.getToolCallsJson());
        dto.put("createdAt", row.getCreatedAt());
        return dto;
    }

    private Map<String, Object> sessionToDto(PhysicianAiChatSession session) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", session.getId());
        dto.put("registerId", session.getRegisterId());
        dto.put("title", session.getTitle());
        dto.put("createdAt", session.getCreatedAt());
        dto.put("updatedAt", session.getUpdatedAt());
        return dto;
    }
}
