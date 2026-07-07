package com.xikang.physician.client;

import com.xikang.common.agent.AgentContextHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MedtechFollowUpClient {

    private final RestTemplate restTemplate;
    private final String medtechBaseUrl;
    private final String internalToken;

    public MedtechFollowUpClient(
        @Qualifier("medtechRestTemplate") RestTemplate restTemplate,
        @Value("${services.medtech-service.url:http://localhost:8093}") String medtechBaseUrl,
        @Value("${xikang.internal.ai.token:${INTERNAL_AI_TOKEN:}}") String internalToken
    ) {
        this.restTemplate = restTemplate;
        this.medtechBaseUrl = medtechBaseUrl;
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    public void notifyVisitEnded(Long registerId, LocalDateTime visitEndedAt, Long employeeId, Long departmentId) {
        if (registerId == null || internalToken.isBlank()) {
            if (internalToken.isBlank()) {
                log.debug("未配置 INTERNAL_AI_TOKEN，跳过随访 visit-ended 通知 registerId={}", registerId);
            }
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("registerId", registerId);
        body.put("visitEndedAt", visitEndedAt != null ? visitEndedAt.toString() : LocalDateTime.now().toString());
        body.put("employeeId", employeeId);
        body.put("departmentId", departmentId);
        try {
            restTemplate.postForEntity(
                medtechBaseUrl + "/api/medtech/internal/follow-up/visit-ended",
                new org.springframework.http.HttpEntity<>(body, headers()),
                Map.class
            );
        } catch (RestClientException ex) {
            log.warn("随访 visit-ended 通知失败 registerId={}: {}", registerId, ex.getMessage());
        }
    }

    private org.springframework.http.HttpHeaders headers() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(AgentContextHeaders.INTERNAL_TOKEN, internalToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
