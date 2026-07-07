package com.xikang.ctviewer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.ctviewer.config.CtViewerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

/**
 * lung-nodule-seg-service HTTP 客户端。
 * <p>
 * 调用 lung-nodule-seg-service（默认 http://127.0.0.1:8222）执行 AI 肺结节分割。
 * 请求/响应格式与 ct-viewer-algo 保持一致（JSON body，{code,message,data} 包装）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LungNoduleSegClient {

    private final RestTemplate lungNoduleSegRestTemplate;
    private final CtViewerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行 AI 肺结节分割。
     *
     * @param srcNrrdPath  源 NRRD 文件的绝对路径（服务可直接读取）
     * @param outNrrdPath  输出掩码 NRRD 的绝对路径（服务写入）
     * @param sourceName   体数据来源名称（可为 null）
     * @return data 字段的 Map，包含 is_mask / meta / lesions / summary / message
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> segment(
        String srcNrrdPath,
        String outNrrdPath,
        String sourceName
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("src_nrrd_path", srcNrrdPath);
        body.put("out_nrrd_path", outNrrdPath);
        body.put("source_name", sourceName == null ? "" : sourceName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = properties.getLungNodule().getBaseUrl() + "/internal/segment";
        try {
            long startedAt = System.currentTimeMillis();
            log.info("lung-nodule-seg-service segment start | srcNrrd={} outNrrd={}", srcNrrdPath, outNrrdPath);
            String json = lungNoduleSegRestTemplate.postForObject(url, entity, String.class);
            log.info(
                "lung-nodule-seg-service segment finished | srcNrrd={} elapsedMs={}",
                srcNrrdPath,
                System.currentTimeMillis() - startedAt
            );
            if (json == null || json.isBlank()) {
                throw new BusinessException(500, "肺结节分割服务无响应");
            }
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt(Result.ERROR_CODE);
            if (code == Result.SUCCESS_CODE) {
                JsonNode dataNode = root.path("data");
                if (dataNode.isMissingNode() || dataNode.isNull()) {
                    return Map.of();
                }
                return objectMapper.convertValue(dataNode, Map.class);
            }
            String message = root.path("message").asText("肺结节分割失败");
            throw new BusinessException(mapHttpStatus(code), mapFriendlyMessage(code, message));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("lung-nodule-seg-service request failed | srcNrrd={}", srcNrrdPath, ex);
            throw new BusinessException(503, mapConnectionErrorMessage(ex), ex);
        } catch (Exception ex) {
            log.warn("lung-nodule-seg-service response parse failed | srcNrrd={}", srcNrrdPath, ex);
            throw new BusinessException(500, "肺结节分割服务响应解析失败", ex);
        }
    }

    /**
     * 健康检查：服务是否就绪且模型已加载。
     */
    public boolean healthCheck() {
        Map<String, Object> status = healthStatus();
        return Boolean.TRUE.equals(status.get("model_loaded"));
    }

    /**
     * 健康检查详情：包含模型加载状态，以及推理中阶段/耗时（如服务端支持）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthStatus() {
        try {
            Map<String, Object> response = lungNoduleSegRestTemplate.getForObject(
                properties.getLungNodule().getBaseUrl() + "/health",
                Map.class
            );
            return response != null ? response : Map.of("model_loaded", false);
        } catch (RestClientException ex) {
            log.warn("lung-nodule-seg-service health check failed: {}", ex.getMessage());
            return Map.of(
                "model_loaded", false,
                "error", ex.getMessage()
            );
        }
    }

    private int mapHttpStatus(int businessCode) {
        return switch (businessCode) {
            case 4001, 4221 -> 400;
            case 5002, 5003 -> 503;
            default -> 500;
        };
    }

    private String mapFriendlyMessage(int code, String fallback) {
        return switch (code) {
            case 4001 -> "源文件不存在或格式不支持";
            case 4221 -> fallback != null && !fallback.isBlank() ? fallback : "文件预处理失败";
            case 5001 -> fallback != null && !fallback.isBlank() ? fallback : "AI 推理失败";
            case 5002 -> "肺结节分割模型未加载，请先放置权重文件 models/best_model.pth 并重启服务";
            case 5003 -> "肺结节分割服务繁忙，请稍后重试";
            default -> fallback != null && !fallback.isBlank() ? fallback : "肺结节分割失败";
        };
    }

    private String mapConnectionErrorMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ConnectException) {
                String baseUrl = properties.getLungNodule().getBaseUrl();
                return "肺结节分割服务未启动，请先启动 lung-nodule-seg-service（" + baseUrl + "）";
            }
            cursor = cursor.getCause();
        }
        return "无法连接肺结节分割服务，请确认 lung-nodule-seg-service 已启动";
    }
}
