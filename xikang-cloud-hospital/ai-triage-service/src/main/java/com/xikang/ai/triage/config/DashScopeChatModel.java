package com.xikang.ai.triage.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义通义千问 Chat 模型 —— 走 OpenAI 兼容路径，但适配专属实例的非标准响应。
 *
 * <p><b>背景</b>：阿里云百炼"专属实例"的 {@code /compatible-mode/v1/chat/completions}
 * 虽然叫"兼容模式"，但返回的是<b>简化格式</b>，不是标准 OpenAI 格式：
 * <pre>
 * // 专属实例返回的格式（非标准）
 * {"finish_reason":"stop","text":"您好..."}
 *
 * // 标准 OpenAI 格式（Spring AI 期望的）
 * {"choices":[{"message":{"role":"assistant","content":"您好..."},"finish_reason":"stop"}]}
 * </pre>
 *
 * <p>Spring AI 1.0 的 {@code OpenAiChatModel} 严格按标准格式解析，
 * 遇到 {@code text} 字段会解析失败，整体返回 404-like 错误。
 *
 * <p>本类直接实现 {@link ChatModel}，把请求转发到 compatible-mode 路径，
 * 然后把简化格式的响应包装成 {@link ChatResponse}，让 RAG 流程能正常使用专属实例的 Chat 服务。
 *
 * <p>仅在 {@code spring.ai.rag.enabled=true} 时装配，并标记为 {@link Primary} 覆盖
 * AiConfig 里基于 OpenAiChatModel 的 Bean。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
public class DashScopeChatModel implements ChatModel {

    /** OpenAI 兼容路径的 Chat Completions endpoint。Spring AI 会在 base-url 后追加这个路径。 */
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modelName;

    public DashScopeChatModel(
            @Value("${spring.ai.dashscope.chat-base-url}") String chatBaseUrl,
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${spring.ai.dashscope.chat-model}") String chatModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.modelName = chatModel;

        // chat-base-url = {DASHSCOPE_HOST}/compatible-mode
        // Spring AI 期望的完整 URL = {chat-base-url}/v1/chat/completions
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(120));   // Chat 比 Embedding 慢，给 2 分钟

        this.restClient = RestClient.builder()
                .baseUrl(chatBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[DashScopeChatModel] 初始化完成：baseUrl={}, model={}", chatBaseUrl, modelName);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 1. 把 Spring AI 的 Prompt 转成 DashScope 期望的 messages 数组
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message msg : prompt.getInstructions()) {
            String role;
            if (msg instanceof UserMessage) {
                role = "user";
            } else if (msg instanceof SystemMessage) {
                role = "system";
            } else if (msg instanceof AssistantMessage) {
                role = "assistant";
            } else {
                role = "user";  // 兜底：未知类型当 user 处理
            }
            messages.add(Map.of("role", role, "content", msg.getText()));
        }

        // 2. 构造请求 body（标准 OpenAI 格式，DashScope 能识别）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("stream", false);
        // 透传 ChatOptions 里的 temperature 等（如果有）
        if (prompt.getOptions() != null) {
            // 简化处理：暂不透传 options，prompt 模板里已经包含所有指令
        }

        long t0 = System.currentTimeMillis();
        // 偶发 Connection reset（阿里云专属实例对请求体大小或长连接复用敏感），
        // 重试 2 次通常能恢复。两次失败再抛出，给上层 fallback。
        String responseJson = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                responseJson = restClient.post()
                        .uri(CHAT_COMPLETIONS_PATH)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                if (attempt > 1) {
                    log.info("[DashScopeChatModel] 第 {} 次重试成功", attempt);
                }
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("[DashScopeChatModel] Chat 调用第 {} 次失败: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        // 重试前等一小段时间（200ms / 800ms），让服务端有时间恢复
                        Thread.sleep(attempt == 1 ? 200L : 800L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (responseJson == null) {
            log.error("[DashScopeChatModel] Chat 3 次重试后仍失败", lastException);
            throw new RuntimeException("DashScope Chat 调用失败: " + lastException.getMessage(), lastException);
        }
        try {
            long elapsed = System.currentTimeMillis() - t0;

            // 3. 解析响应：兼容两种格式
            //    - 简化格式：{"finish_reason":"stop","text":"..."}
            //    - 标准格式：{"choices":[{"message":{"content":"..."},"finish_reason":"..."}]}
            JsonNode root = objectMapper.readTree(responseJson);
            String content;
            String finishReason;

            JsonNode choicesNode = root.path("choices");
            if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                // 标准格式
                JsonNode firstChoice = choicesNode.get(0);
                content = firstChoice.path("message").path("content").asText("");
                finishReason = firstChoice.path("finish_reason").asText("stop");
            } else {
                // 简化格式（专属实例实际返回的）
                content = root.path("text").asText("");
                finishReason = root.path("finish_reason").asText("stop");
            }

            // qwen-max 经常在 JSON 前后加说明文字或 markdown 代码块，
            // BeanOutputConverter 用严格 JSON 解析会把这种 content 直接反序列化为空对象。
            // 先在 content 里挑出最外层的 {...} 子串作为最终输出。
            String normalized = extractFirstJsonObject(content);
            if (!normalized.equals(content)) {
                log.warn("[DashScopeChatModel] 非纯 JSON 响应，已提取 JSON 子串：原 {} 字符 → 新 {} 字符",
                        content.length(), normalized.length());
            }

            AssistantMessage assistantMessage = new AssistantMessage(normalized);
            Generation generation = new Generation(assistantMessage,
                    ChatGenerationMetadata.builder().finishReason(finishReason).build());
            log.info("[DashScopeChatModel] Chat 完成，耗时 {} ms，返回 {} 字符", elapsed, normalized.length());
            return new ChatResponse(List.of(generation));
        } catch (Exception e) {
            log.error("[DashScopeChatModel] Chat 调用失败", e);
            throw new RuntimeException("DashScope Chat 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从模型返回的文本中提取第一个平衡的顶层 JSON 对象子串。
     *
     * <p>qwen-max / 通义系列经常在 JSON 前后包裹说明文字或 markdown 代码块，例如：
     * <pre>
     *   "好的，根据症状分析如下：\n```json\n{\"urgencyLevel\":\"IV\",...}\n```\n建议尽快就医。"
     * </pre>
     * BeanOutputConverter 直接反序列化会失败 → 得到空对象 → "推荐内科/无 AI 分析"。
     *
     * <p>本方法用括号配对扫描，定位第一个 {@code {} 平衡} 的子串；如果原文本已经能
     * 直接 parse 成 JSON 则原样返回（避免误改）。
     */
    static String extractFirstJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String trimmed = text.trim();
        // 1. 原样可解析 → 不动它
        try {
            new ObjectMapper().readTree(trimmed);
            return trimmed;
        } catch (Exception ignore) {
            // 走到下面去提取
        }
        // 2. 找到第一个 { 后做括号配对
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return trimmed;  // 没找到 JSON 字符，原样返回（让上层抛异常暴露根因）
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        // 没配对完成（说明模型返回了截断的 JSON），原样返回让上层报错
        return trimmed;
    }
}
