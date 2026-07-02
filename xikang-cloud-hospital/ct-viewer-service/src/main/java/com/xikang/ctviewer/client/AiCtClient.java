package com.xikang.ctviewer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.ctviewer.config.CtViewerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Map;

@Slf4j
@Component
public class AiCtClient {

    private final RestTemplate aiCtRestTemplate;
    private final CtViewerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiCtClient(
        @Qualifier("aiCtRestTemplate") RestTemplate aiCtRestTemplate,
        CtViewerProperties properties
    ) {
        this.aiCtRestTemplate = aiCtRestTemplate;
        this.properties = properties;
    }

    public Map<String, Object> analyze(byte[] niftiBytes, String fileName) {
        if (niftiBytes == null || niftiBytes.length == 0) {
            throw new BusinessException(400, "NIfTI 体数据为空，无法分析");
        }
        String safeName = fileName == null || fileName.isBlank() ? "volume.nii.gz" : fileName.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(niftiBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        String url = properties.getAiCt().getBaseUrl() + "/analyze";
        try {
            String json = aiCtRestTemplate.postForObject(
                url,
                new HttpEntity<>(body, headers),
                String.class
            );
            return parseAnalyzeResponse(json);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("ai-ct-service request failed | fileName={}", safeName, ex);
            throw new BusinessException(503, mapConnectionErrorMessage(ex), ex);
        } catch (Exception ex) {
            log.warn("ai-ct-service response parse failed | fileName={}", safeName, ex);
            throw new BusinessException(500, "CT 伪影分析服务响应解析失败", ex);
        }
    }

    public boolean healthCheck() {
        try {
            Map<?, ?> response = aiCtRestTemplate.getForObject(
                properties.getAiCt().getBaseUrl() + "/health",
                Map.class
            );
            return response != null && Boolean.TRUE.equals(response.get("model_loaded"));
        } catch (RestClientException ex) {
            log.warn("ai-ct-service health check failed: {}", ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAnalyzeResponse(String json) throws Exception {
        if (json == null || json.isBlank()) {
            throw new BusinessException(500, "CT 伪影分析服务无响应");
        }

        JsonNode root = objectMapper.readTree(json);
        int code = root.path("code").asInt(Result.ERROR_CODE);
        String message = root.path("message").asText("CT 伪影分析失败");

        if (code == Result.SUCCESS_CODE) {
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                throw new BusinessException(500, "CT 伪影分析服务未返回结果数据");
            }
            return objectMapper.convertValue(dataNode, Map.class);
        }

        throw new BusinessException(mapBusinessCode(code), mapFriendlyMessage(code, message));
    }

    private int mapBusinessCode(int code) {
        return switch (code) {
            case 4001, 4221 -> 400;
            case 5002, 5003 -> 503;
            default -> 500;
        };
    }

    private String mapFriendlyMessage(int code, String fallback) {
        return switch (code) {
            case 4001 -> "文件格式无效，仅支持 NIfTI (.nii/.nii.gz)";
            case 4221 -> fallback != null && !fallback.isBlank()
                ? fallback
                : "NIfTI 文件解析失败";
            case 5001 -> fallback != null && !fallback.isBlank()
                ? fallback
                : "CT 伪影推理失败";
            case 5002 -> "模型权重未加载，无法执行 CT 伪影分析";
            case 5003 -> "CT 伪影分析服务繁忙，请稍后重试";
            default -> fallback != null && !fallback.isBlank()
                ? fallback
                : "CT 伪影分析失败";
        };
    }

    private String mapConnectionErrorMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ConnectException) {
                String baseUrl = properties.getAiCt().getBaseUrl();
                return "CT 伪影分析服务未启动，请先启动 ai-ct-service（" + baseUrl + "）";
            }
            cursor = cursor.getCause();
        }
        if (ex instanceof ResourceAccessException) {
            return "无法连接 CT 伪影分析服务，请确认 ai-ct-service 已启动且地址配置正确";
        }
        return "CT 伪影分析服务暂时不可用";
    }
}
