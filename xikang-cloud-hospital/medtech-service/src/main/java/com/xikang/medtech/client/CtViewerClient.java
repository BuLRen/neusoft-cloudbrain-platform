package com.xikang.medtech.client;

import com.xikang.common.agent.AgentContextHeaders;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * medtech-service 调 ct-viewer-service（校验 volume 是否存在、同步绑定信息）。
 */
@Slf4j
@Service
public class CtViewerClient {

    private final RestTemplate restTemplate;
    private final String ctViewerBaseUrl;
    private final String internalToken;

    public CtViewerClient(
            @Qualifier("ctViewerRestTemplate") RestTemplate restTemplate,
            @Value("${services.ct-viewer-service.url:http://localhost:8099}") String ctViewerBaseUrl,
            @Value("${services.ct-viewer-service.internal-token:${INTERNAL_SERVICE_TOKEN:${INTERNAL_AI_TOKEN:}}}") String internalToken) {
        this.restTemplate = restTemplate;
        this.ctViewerBaseUrl = ctViewerBaseUrl;
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    public void assertVolumeExists(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (StringUtils.hasText(internalToken)) {
            getVolumeMetaInternal(volumeId.trim());
            return;
        }
        getVolumeMeta(volumeId.trim());
    }

    public void bindVolume(String volumeId, Long checkRequestId, Long departmentId, Long registerId) {
        if (!StringUtils.hasText(internalToken)) {
            log.warn("未配置 ct-viewer internal token，跳过 volume 绑定同步 | volumeId={}", volumeId);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("checkRequestId", checkRequestId);
        body.put("departmentId", departmentId);
        body.put("registerId", registerId);
        exchangeInternal(
            HttpMethod.PUT,
            ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/bind",
            body,
            volumeId,
            "同步 CT 影像绑定失败"
        );
    }

    public void unbindVolume(String volumeId) {
        if (!StringUtils.hasText(volumeId) || !StringUtils.hasText(internalToken)) {
            return;
        }
        exchangeInternal(
            HttpMethod.DELETE,
            ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/bind",
            null,
            volumeId,
            "同步 CT 影像解绑失败"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> analyzeVolume(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(500, "未配置 ct-viewer internal token，无法执行 CT 影像分析");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            Map<String, Object> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/analyze",
                HttpMethod.POST,
                entity,
                Map.class,
                Map.of("volumeId", volumeId.trim())
            ).getBody();
            return extractData(response, "CT 影像分析失败");
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 分析失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 影像分析服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> segmentVolume(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(500, "未配置 ct-viewer internal token，无法执行 CT 病灶分割");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), headers);
            Map<String, Object> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/segment",
                HttpMethod.POST,
                entity,
                Map.class,
                Map.of("volumeId", volumeId.trim())
            ).getBody();
            return extractData(response, "CT 病灶分割失败");
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 分割失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 病灶分割服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> aiSegmentVolume(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(500, "未配置 ct-viewer internal token，无法执行 AI 肺结节分割");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), headers);
            Map<String, Object> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/segment/ai",
                HttpMethod.POST,
                entity,
                Map.class,
                Map.of("volumeId", volumeId.trim())
            ).getBody();
            return extractData(response, "AI 肺结节分割失败");
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service AI 分割失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "AI 肺结节分割服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> getVolumeMeta(String volumeId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    ctViewerBaseUrl + "/api/ct-viewer/volume/{volumeId}/meta",
                    Map.class,
                    Map.of("volumeId", volumeId));
            return extractData(response, "CT 影像不存在或已过期");
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 影像服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> getVolumeMetaInternal(String volumeId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            Map<String, Object> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/meta",
                HttpMethod.GET,
                entity,
                Map.class,
                Map.of("volumeId", volumeId)
            ).getBody();
            return extractData(response, "CT 影像不存在或已过期");
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 内部 meta 失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 影像服务暂时不可用", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void exchangeInternal(
        HttpMethod method,
        String url,
        Map<String, Object> body,
        String volumeId,
        String failMessage
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
            HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.exchange(
                url,
                method,
                entity,
                Map.class,
                Map.of("volumeId", volumeId)
            ).getBody();
            extractData(response, failMessage);
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 内部接口失败 | volumeId={} method={}", volumeId, method, e);
            throw new BusinessException(500, failMessage, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response, String failMessage) {
        if (response == null) {
            throw new BusinessException(500, "CT 影像服务无响应");
        }
        Object code = response.get("code");
        if (!(code instanceof Number) || ((Number) code).intValue() != Result.SUCCESS_CODE) {
            Object msg = response.get("message");
            throw new BusinessException(404, msg instanceof String ? (String) msg : failMessage);
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> result = new HashMap<>();
            dataMap.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }
}
