package com.xikang.medtech.client;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * medtech-service 调 ct-viewer-service（校验 volume 是否存在）。
 */
@Slf4j
@Service
public class CtViewerClient {

    private final RestTemplate restTemplate;
    private final String ctViewerBaseUrl;

    public CtViewerClient(
            @Qualifier("ctViewerRestTemplate") RestTemplate restTemplate,
            @Value("${services.ct-viewer-service.url:http://localhost:8099}") String ctViewerBaseUrl) {
        this.restTemplate = restTemplate;
        this.ctViewerBaseUrl = ctViewerBaseUrl;
    }

    public void assertVolumeExists(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        getVolumeMeta(volumeId.trim());
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
        throw new BusinessException(404, failMessage);
    }
}
