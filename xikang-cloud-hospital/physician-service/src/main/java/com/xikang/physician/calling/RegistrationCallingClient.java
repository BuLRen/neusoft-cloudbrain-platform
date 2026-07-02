package com.xikang.physician.calling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * registration-service 叫号内部接口的 HTTP 客户端。
 *
 * physician-service 不直接读写 register 表的叫号字段，而是通过 HTTP 调 registration-service 的：
 *   POST /api/registration/calling/internal/call-next
 *   POST /api/registration/calling/internal/call/{registerId}
 *   POST /api/registration/calling/internal/answer/{registerId}
 *   POST /api/registration/calling/internal/pass/{registerId}
 *   GET  /api/registration/calling/current?employeeId=...
 *
 * 设计文档 §5.4：physician-service 通过 Feign 调 registration-service。
 * 本项目 physician-service 当前没启用 Feign，统一用 RestTemplate 风格（与 DifyWorkflowClient 一致）。
 *
 * URL 配置：calling.service.url，默认 http://localhost:8091（直连 registration-service）
 *   - 单机开发：默认值即可
 *   - 多实例/上 Nacos 后：改用 lb 协议（需先在 physician-service 启用 Feign）
 */
@Slf4j
@Component
public class RegistrationCallingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RegistrationCallingClient(
            @Value("${calling.service.url:http://localhost:8091}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 叫下一个 */
    public Map<String, Object> callNext(Long employeeId) {
        return post("/api/registration/calling/internal/call-next",
                Map.of("employeeId", employeeId));
    }

    /** 叫指定号 */
    public Map<String, Object> callSpecific(Long registerId, Long operatorEmployeeId) {
        return post("/api/registration/calling/internal/call/" + registerId,
                operatorEmployeeId == null ? Map.of() : Map.of("employeeId", operatorEmployeeId));
    }

    /** 患者应答 */
    public Map<String, Object> answer(Long registerId) {
        return post("/api/registration/calling/internal/answer/" + registerId, Map.of());
    }

    /** 标记过号 */
    public Map<String, Object> pass(Long registerId) {
        return post("/api/registration/calling/internal/pass/" + registerId, Map.of());
    }

    /** 查当前叫号 */
    public Map<String, Object> currentCalling(Long employeeId) {
        return get("/api/registration/calling/current?employeeId=" + employeeId);
    }

    // ==================== 内部 ====================

    private Map<String, Object> post(String path, Object body) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            return parseResult(resp.getBody(), url);
        } catch (Exception e) {
            log.error("调 registration-service 失败 POST url={}: {}", url, e.getMessage());
            return Map.of("code", 500, "message", "调叫号服务失败：" + e.getMessage());
        }
    }

    private Map<String, Object> get(String path) {
        String url = baseUrl + path;
        try {
            String body = restTemplate.getForObject(url, String.class);
            return parseResult(body, url);
        } catch (Exception e) {
            log.error("调 registration-service 失败 GET url={}: {}", url, e.getMessage());
            return Map.of("code", 500, "message", "调叫号服务失败：" + e.getMessage());
        }
    }

    /**
     * 解析 registration-service 的统一 Result 格式：{code, message, data}
     * 失败时返回 Map.of("code", code, "message", msg)
     * 成功时返回 Map.of("code", 200, "data", data)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(String body, String url) {
        if (body == null || body.isBlank()) {
            return Map.of("code", 500, "message", "registration-service 返回空响应");
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            int code = root.path("code").asInt(500);
            String message = root.path("message").asText("");
            JsonNode data = root.path("data");
            if (code == 200) {
                if (data.isObject()) {
                    return MAPPER.convertValue(data, Map.class);
                }
                return Map.of("code", 200, "data", MAPPER.convertValue(data, Object.class));
            }
            // 业务失败：把 message 透传给前端
            return Map.of("code", code, "message", message);
        } catch (Exception e) {
            log.error("解析 registration-service 响应失败 url={}, body={}: {}", url, body, e.getMessage());
            return Map.of("code", 500, "message", "解析叫号服务响应失败");
        }
    }
}
