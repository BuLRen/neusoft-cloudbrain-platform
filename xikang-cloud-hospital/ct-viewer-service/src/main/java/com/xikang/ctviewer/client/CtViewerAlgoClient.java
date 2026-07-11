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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CtViewerAlgoClient {

    private final RestTemplate ctViewerAlgoRestTemplate;
    private final CtViewerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> convert(String kind, String srcPath, String outNrrdPath, String sourceName) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", kind);
        body.put("src_path", srcPath);
        body.put("out_nrrd_path", outNrrdPath);
        body.put("source_name", sourceName);
        return postInternal("/internal/convert", body);
    }

    public Map<String, Object> meta(String nrrdPath, String sourceName) {
        Map<String, Object> body = new HashMap<>();
        body.put("nrrd_path", nrrdPath);
        body.put("source_name", sourceName);
        return postInternal("/internal/meta", body);
    }

    public Map<String, Object> filter(
        String srcNrrdPath,
        String outNrrdPath,
        String filterName,
        Map<String, Object> params,
        String sourceName
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("src_nrrd_path", srcNrrdPath);
        body.put("out_nrrd_path", outNrrdPath);
        body.put("filter_name", filterName);
        body.put("params", params == null ? Map.of() : params);
        body.put("source_name", sourceName);
        return postInternal("/internal/filter", body);
    }

    public Map<String, Object> segment(
        String srcNrrdPath,
        String outNrrdPath,
        String sourceName,
        Map<String, Object> params
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("src_nrrd_path", srcNrrdPath);
        body.put("out_nrrd_path", outNrrdPath);
        body.put("source_name", sourceName);
        body.put("params", params == null ? Map.of() : params);
        return postInternal("/internal/segment", body);
    }

    public String export(String srcNrrdPath, String outPath, String format) {
        Map<String, Object> body = new HashMap<>();
        body.put("src_nrrd_path", srcNrrdPath);
        body.put("out_path", outPath);
        body.put("format", format);
        Map<String, Object> data = postInternal("/internal/export", body);
        Object path = data.get("path");
        if (path == null) {
            throw new BusinessException(500, "算法服务未返回导出路径");
        }
        return String.valueOf(path);
    }

    public boolean healthCheck() {
        try {
            Map<?, ?> response = ctViewerAlgoRestTemplate.getForObject(
                properties.getAlgo().getBaseUrl() + "/health",
                Map.class
            );
            return response != null && Boolean.TRUE.equals(response.get("ok"));
        } catch (RestClientException ex) {
            log.warn("ct-viewer-algo health check failed: {}", ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postInternal(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = properties.getAlgo().getBaseUrl() + path;
        try {
            String json = ctViewerAlgoRestTemplate.postForObject(url, entity, String.class);
            if (json == null || json.isBlank()) {
                throw new BusinessException(500, "图像算法服务无响应");
            }
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt(Result.ERROR_CODE);
            if (code != Result.SUCCESS_CODE) {
                String message = root.path("message").asText("图像算法服务调用失败");
                throw new BusinessException(500, message);
            }
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                return Map.of();
            }
            return objectMapper.convertValue(dataNode, Map.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("ct-viewer-algo request failed | path={}", path, ex);
            throw new BusinessException(500, "图像算法服务暂时不可用", ex);
        } catch (Exception ex) {
            log.warn("ct-viewer-algo response parse failed | path={}", path, ex);
            throw new BusinessException(500, "图像算法服务响应解析失败", ex);
        }
    }
}
