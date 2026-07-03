package com.xikang.medtech.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DifyWorkflowClient {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;
    private final RestTemplate restTemplate;

    public DifyWorkflowClient(DifyAiProperties properties) {
        this.properties = properties;
        this.restTemplate = createRestTemplate(properties);
    }

    private static RestTemplate createRestTemplate(DifyAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    public boolean isCheckSimulateEnabled() {
        return properties.isDifyBaseConfigured()
            && properties.isCheckSimulateWorkflowSwitchOn()
            && !properties.resolveCheckSimulateApiKey().isBlank();
    }

    public DifyWorkflowRunResult runCheckSimulateBlocking(Map<String, Object> inputs, String user, String traceId) {
        if (!properties.isDifyBaseConfigured()) {
            throw new DifyWorkflowException("Dify 未启用或未配置 base-url");
        }
        String apiKey = properties.resolveCheckSimulateApiKey();
        if (apiKey.isBlank()) {
            throw new DifyWorkflowException("模拟检查 API Key 未配置");
        }
        return runWorkflowBlockingInternal(properties.getBaseUrl(), apiKey, inputs, user, traceId);
    }

    public boolean isFollowUpCaseSummaryEnabled() {
        return properties.isFollowUpCaseSummaryEnabled();
    }

    public boolean isFollowUpMedicalChatEnabled() {
        return properties.isFollowUpMedicalChatEnabled();
    }

    public DifyWorkflowRunResult runFollowUpCaseSummaryBlocking(Map<String, Object> inputs, String user, String traceId) {
        String apiKey = properties.resolveFollowUpCaseSummaryApiKey();
        if (apiKey.isBlank()) {
            throw new DifyWorkflowException("随访病例总结 API Key 未配置");
        }
        return runWorkflowBlockingInternal(properties.getBaseUrl(), apiKey, inputs, user, traceId);
    }

    public DifyWorkflowRunResult runFollowUpMedicalChatBlocking(Map<String, Object> inputs, String user, String traceId) {
        String apiKey = properties.resolveFollowUpMedicalChatApiKey();
        if (apiKey.isBlank()) {
            throw new DifyWorkflowException("随访医疗对话 API Key 未配置");
        }
        return runWorkflowBlockingInternal(properties.getBaseUrl(), apiKey, inputs, user, traceId);
    }

    private DifyWorkflowRunResult runWorkflowBlockingInternal(
        String baseUrl,
        String apiKey,
        Map<String, Object> inputs,
        String user,
        String traceId
    ) {
        String url = baseUrl.replaceAll("/$", "") + "/v1/workflows/run";
        log.info("Dify check simulate blocking request traceId={} baseUrl={}", traceId, baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs == null ? Map.of() : inputs);
        body.put("response_mode", "blocking");
        body.put("user", user == null || user.isBlank() ? "medtech-service" : user);

        log.debug("Dify workflow inputs keys={}", inputs == null ? List.of() : inputs.keySet());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new DifyWorkflowException("Dify 工作流返回非成功状态", response.getStatusCode().value());
            }
            return parseBlockingResponse(response.getBody());
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            String message = extractHttpErrorMessage(ex.getResponseBodyAsString());
            log.warn("Dify HTTP error status={}: {}", ex.getStatusCode().value(), message);
            throw new DifyWorkflowException(message, ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Dify workflow request failed: {}", ex.getMessage());
            throw new DifyWorkflowException("Dify 工作流请求失败，请稍后重试");
        }
    }

    private DifyWorkflowRunResult parseBlockingResponse(String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        String workflowRunId = textOrNull(root.path("workflow_run_id"));
        JsonNode data = root.path("data");
        String status = textOrNull(data.path("status"));
        String error = textOrNull(data.path("error"));
        Double elapsed = data.path("elapsed_time").isNumber() ? data.path("elapsed_time").asDouble() : null;

        if ("paused".equalsIgnoreCase(status)) {
            throw new DifyWorkflowException("工作流已暂停并需人工介入，当前门诊系统暂不支持该模式");
        }
        if ("failed".equalsIgnoreCase(status)) {
            String reason = error != null && !error.isBlank() ? error : "工作流执行失败";
            log.warn("Dify workflow failed runId={}: {}", workflowRunId, reason);
            throw new DifyWorkflowException("AI 工作流执行失败：" + reason);
        }
        if (!"succeeded".equalsIgnoreCase(status)) {
            throw new DifyWorkflowException("AI 工作流未成功完成，状态：" + (status == null ? "unknown" : status));
        }

        Map<String, Object> outputs = parseOutputs(data.path("outputs"));
        return new DifyWorkflowRunResult(status, outputs, error, workflowRunId, elapsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOutputs(JsonNode outputsNode) throws Exception {
        if (outputsNode == null || outputsNode.isMissingNode() || outputsNode.isNull()) {
            return Map.of();
        }
        if (outputsNode.isObject()) {
            Map<String, Object> map = MAPPER.convertValue(outputsNode, Map.class);
            if (hasStructuredOutputKey(map)) {
                return map;
            }
            JsonNode textNode = outputsNode.path("text");
            if (textNode.isTextual() && textNode.asText().trim().startsWith("{")) {
                try {
                    return MAPPER.readValue(textNode.asText(), Map.class);
                } catch (Exception ignored) {
                    if (map.size() == 1 && map.containsKey("text")) {
                        return map;
                    }
                }
            }
            return map;
        }
        return Map.of();
    }

    private static String extractHttpErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Dify API 请求失败";
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            String message = textOrNull(root.path("message"));
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "Dify API 请求失败";
    }

    private static boolean hasStructuredOutputKey(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (String key : new String[] { "structured_output", "structuredOutput", "output_structured" }) {
            Object value = map.get(key);
            if (value instanceof Map<?, ?> nested && !nested.isEmpty()) {
                return true;
            }
            if (value instanceof String text && text.trim().startsWith("{")) {
                return true;
            }
        }
        return map.containsKey("checkName") && map.containsKey("resultItems");
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
