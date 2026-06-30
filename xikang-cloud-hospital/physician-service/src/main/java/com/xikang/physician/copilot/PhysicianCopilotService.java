package com.xikang.physician.copilot;

import com.xikang.physician.context.PhysicianAuthContext;
import com.xikang.physician.copilot.entity.PhysicianAiChatMessage;
import com.xikang.physician.copilot.mapper.PhysicianAiChatMessageMapper;
import com.xikang.physician.service.PhysicianService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class PhysicianCopilotService {

    private static final int HISTORY_LIMIT = 40;

    private final ChatClient copilotChatClient;
    private final PhysicianCopilotContextBuilder contextBuilder;
    private final PhysicianAiChatMessageMapper chatMessageMapper;
    private final PhysicianService physicianService;

    public PhysicianCopilotService(
        @Qualifier("physicianCopilotChatClient") ChatClient copilotChatClient,
        PhysicianCopilotContextBuilder contextBuilder,
        PhysicianAiChatMessageMapper chatMessageMapper,
        PhysicianService physicianService
    ) {
        this.copilotChatClient = copilotChatClient;
        this.contextBuilder = contextBuilder;
        this.chatMessageMapper = chatMessageMapper;
        this.physicianService = physicianService;
    }

    public List<Map<String, Object>> getHistory(Long registerId) {
        assertPatientAccess(registerId);
        return chatMessageMapper.selectByRegisterId(registerId, HISTORY_LIMIT).stream()
            .map(this::toDto)
            .toList();
    }

    public void clearHistory(Long registerId) {
        assertPatientAccess(registerId);
        chatMessageMapper.deleteByRegisterId(registerId);
    }

    public Map<String, Object> chat(Long registerId, String userMessage, Consumer<String> tokenConsumer) {
        assertPatientAccess(registerId);
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }

        String trimmed = userMessage.trim();
        saveMessage(registerId, "user", trimmed, null);

        String systemPrompt = contextBuilder.build(registerId);
        List<Message> historyMessages = loadHistoryMessages(registerId);

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
            saveMessage(registerId, "assistant", response, null);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("registerId", registerId);
            meta.put("done", true);
            return meta;
        } catch (Exception ex) {
            log.error("Copilot chat failed registerId={}", registerId, ex);
            throw ex;
        } finally {
            PhysicianCopilotTools.clearRegisterId();
        }
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

    private List<Message> loadHistoryMessages(Long registerId) {
        List<PhysicianAiChatMessage> rows = chatMessageMapper.selectByRegisterId(registerId, HISTORY_LIMIT);
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

    private void saveMessage(Long registerId, String role, String content, String toolCallsJson) {
        PhysicianAiChatMessage row = new PhysicianAiChatMessage();
        row.setRegisterId(registerId);
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
        dto.put("role", row.getRole());
        dto.put("content", row.getContent());
        dto.put("toolCallsJson", row.getToolCallsJson());
        dto.put("createdAt", row.getCreatedAt());
        return dto;
    }
}
