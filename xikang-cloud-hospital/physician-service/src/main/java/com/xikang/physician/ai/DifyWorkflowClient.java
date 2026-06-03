package com.xikang.physician.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dify {@code POST /v1/workflows/run} client (blocking mode). When disabled, callers use {@link FallbackWorkflowEngine}.
 */
@Component
public class DifyWorkflowClient {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyAiProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public DifyWorkflowClient(DifyAiProperties properties) {
        this.properties = properties;
    }

    public boolean isReady() {
        return properties.isEnabled()
            && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
            && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public boolean isPreliminaryEnabled() {
        return isReady()
            && properties.getWorkflowPreliminary() != null
            && !properties.getWorkflowPreliminary().isBlank();
    }

    /**
     * Blocking workflow run with explicit user id and optional trace id.
     */
    public DifyWorkflowRunResult runWorkflowBlocking(Map<String, Object> inputs, String user, String traceId) {
        if (!isReady()) {
            throw new DifyWorkflowException("Dify 未启用或未配置 base-url / api-key");
        }
        String url = properties.getBaseUrl().replaceAll("/$", "") + "/v1/workflows/run";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs == null ? Map.of() : inputs);
        body.put("response_mode", "blocking");
        body.put("user", user == null || user.isBlank() ? "physician-service" : user);

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

    /**
     * Legacy helper for W1–W4: returns empty map on failure (no exception).
     */
    public Map<String, Object> runWorkflow(String workflowEnabledFlag, Map<String, Object> inputs) {
        if (!isReady() || workflowEnabledFlag == null || workflowEnabledFlag.isBlank()) {
            return Map.of();
        }
        try {
            DifyWorkflowRunResult result = runWorkflowBlocking(inputs, "physician-service", null);
            return result.getOutputs();
        } catch (DifyWorkflowException ex) {
            log.warn("Dify workflow skipped: {}", ex.getMessage());
            return Map.of();
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
            JsonNode textNode = outputsNode.path("text");
            if (textNode.isTextual() && textNode.asText().trim().startsWith("{")) {
                try {
                    return MAPPER.readValue(textNode.asText(), Map.class);
                } catch (Exception ignored) {
                    Map<String, Object> map = MAPPER.convertValue(outputsNode, Map.class);
                    if (map.size() == 1 && map.containsKey("text")) {
                        return map;
                    }
                }
            }
            return MAPPER.convertValue(outputsNode, Map.class);
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

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
