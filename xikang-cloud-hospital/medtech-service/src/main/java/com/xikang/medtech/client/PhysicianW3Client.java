package com.xikang.medtech.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Fire-and-forget client to trigger physician-service W3 after result submission.
 */
@Component
public class PhysicianW3Client {

    private static final Logger log = LoggerFactory.getLogger(PhysicianW3Client.class);

    private final RestTemplate restTemplate;
    private final String physicianBaseUrl;
    private final boolean enabled;

    public PhysicianW3Client(
        @Value("${xikang.physician.base-url:http://localhost:8092}") String physicianBaseUrl,
        @Value("${xikang.physician.w3-auto-trigger-enabled:true}") boolean enabled
    ) {
        this.restTemplate = new RestTemplate();
        this.physicianBaseUrl = physicianBaseUrl.replaceAll("/$", "");
        this.enabled = enabled;
    }

    public void triggerW3Async(Long registerId) {
        if (!enabled || registerId == null) {
            return;
        }
        String url = physicianBaseUrl + "/api/physician/ai/w3/trigger-async";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("registerId", registerId), headers);
            restTemplate.postForEntity(url, entity, Map.class);
            log.info("W3 auto-trigger requested | registerId={}", registerId);
        } catch (RestClientException ex) {
            log.warn("W3 auto-trigger request failed | registerId={} reason={}", registerId, ex.getMessage());
        }
    }
}
