package com.xikang.physician.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dify Agent {@code POST /v1/chat-messages} streaming client.
 */
@Component
public class DifyAgentClient {

    private static final Logger log = LoggerFactory.getLogger(DifyAgentClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;
    private final HttpClient httpClient;

    public DifyAgentClient(DifyAiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .build();
    }

    public boolean isEnabled() {
        return properties.isAgentEnabled();
    }

    public DifyAgentChatResult streamChat(
        Map<String, String> inputs,
        String query,
        String user,
        String conversationId,
        Consumer<String> tokenConsumer,
        Consumer<Map<String, Object>> thoughtConsumer
    ) {
        if (!isEnabled()) {
            throw new DifyWorkflowException("Dify Agent 未配置，请设置 DIFY_AGENT_API_KEY");
        }

        String baseUrl = normalizeBaseUrl(properties.resolveAgentBaseUrl());
        String url = baseUrl + "/v1/chat-messages";
        String apiKey = properties.resolveAgentApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs == null ? Map.of() : inputs);
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put("user", user);
        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        try {
            String jsonBody = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new DifyWorkflowException("Dify Agent 请求失败 HTTP " + response.statusCode() + ": " + errBody);
            }

            DifyAgentChatResult.Builder result = DifyAgentChatResult.builder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    parseEvent(payload, result, tokenConsumer, thoughtConsumer);
                }
            }

            DifyAgentChatResult built = result.build();
            if (built.getAnswer().isBlank()) {
                log.warn("Dify Agent returned empty answer user={} conversationId={}", user, built.getConversationId());
            }
            return built;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Dify Agent stream failed", ex);
            throw new DifyWorkflowException("Dify Agent 调用失败: " + ex.getMessage());
        }
    }

    private void parseEvent(
        String payload,
        DifyAgentChatResult.Builder result,
        Consumer<String> tokenConsumer,
        Consumer<Map<String, Object>> thoughtConsumer
    ) throws Exception {
        JsonNode node = MAPPER.readTree(payload);
        String event = text(node, "event");
        result.conversationId(text(node, "conversation_id"));

        if ("error".equals(event)) {
            String message = text(node, "message");
            if (message == null || message.isBlank()) {
                message = payload;
            }
            throw new DifyWorkflowException(message);
        }

        if ("agent_thought".equals(event)) {
            Map<String, Object> thought = new LinkedHashMap<>();
            putIfPresent(thought, "event", event);
            putIfPresent(thought, "id", text(node, "id"));
            putIfPresent(thought, "position", text(node, "position"));
            putIfPresent(thought, "thought", text(node, "thought"));
            putIfPresent(thought, "tool", text(node, "tool"));
            putIfPresent(thought, "toolInput", text(node, "tool_input"));
            putIfPresent(thought, "observation", text(node, "observation"));
            Map<String, Object> merged = result.mergeThought(thought);
            if (thoughtConsumer != null && !merged.isEmpty()) {
                thoughtConsumer.accept(merged);
            }
            return;
        }

        if (isAnswerStreamEvent(event)) {
            emitAnswerChunk(text(node, "answer"), result, tokenConsumer);
            return;
        }

        // 部分 Dify 版本 event 字段缺失，但携带 answer 增量
        String fallbackAnswer = text(node, "answer");
        if (fallbackAnswer != null && !fallbackAnswer.isEmpty()) {
            emitAnswerChunk(fallbackAnswer, result, tokenConsumer);
        }
    }

    private boolean isAnswerStreamEvent(String event) {
        if (event == null || event.isBlank()) {
            return false;
        }
        return switch (event) {
            case "message", "agent_message", "message_replace" -> true;
            default -> false;
        };
    }

    private void emitAnswerChunk(
        String incoming,
        DifyAgentChatResult.Builder result,
        Consumer<String> tokenConsumer
    ) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        String delta = result.resolveAnswerDelta(incoming);
        if (delta.isEmpty()) {
            return;
        }
        String sanitized = DifyAgentAnswerSanitizer.sanitizeChunk(delta);
        if (sanitized.isEmpty()) {
            return;
        }
        result.appendAnswer(sanitized);
        if (tokenConsumer != null) {
            tokenConsumer.accept(sanitized);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
