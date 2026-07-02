package com.xikang.physician.client;

import com.xikang.common.agent.AgentContextHeaders;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * physician-service 调 ct-viewer-service 内部接口（只读代理）。
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
        @Value("${services.ct-viewer-service.internal-token:${INTERNAL_SERVICE_TOKEN:${INTERNAL_AI_TOKEN:}}}") String internalToken
    ) {
        this.restTemplate = restTemplate;
        this.ctViewerBaseUrl = ctViewerBaseUrl;
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    public byte[] fetchVolumeNrrd(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(500, "未配置 ct-viewer internal token，无法读取 CT 影像");
        }
        try {
            HttpHeaders headers = internalHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/nrrd",
                HttpMethod.GET,
                entity,
                byte[].class,
                volumeId.trim()
            );
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(404, "CT 影像不存在或已过期");
            }
            return body;
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 读取 NRRD 失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 影像服务暂时不可用", e);
        }
    }

    public Map<String, Object> fetchVolumeMeta(String volumeId) {
        if (!StringUtils.hasText(volumeId)) {
            throw new BusinessException(400, "volumeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(500, "未配置 ct-viewer internal token，无法读取 CT 影像元数据");
        }
        try {
            HttpHeaders headers = internalHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Result<Map<String, Object>>> response = restTemplate.exchange(
                ctViewerBaseUrl + "/api/ct-viewer/internal/volume/{volumeId}/meta",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {},
                volumeId.trim()
            );
            Result<Map<String, Object>> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BusinessException(404, "CT 影像元数据不存在或已过期");
            }
            return body.getData();
        } catch (RestClientException e) {
            log.warn("调 ct-viewer-service 读取 meta 失败 | volumeId={}", volumeId, e);
            throw new BusinessException(500, "CT 影像服务暂时不可用", e);
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
        return headers;
    }
}
