package com.xikang.medtech.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.config.GlucosePredictionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlucosePredictionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GlucosePredictionProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    public Map<String, Object> predict(Long registerId, List<Map<String, Object>> observations) {
        RestTemplate restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
            .build();

        Map<String, Object> body = new HashMap<>();
        body.put("register_id", registerId);
        body.put("observations", observations);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String json = restTemplate.postForObject(
                properties.getBaseUrl() + "/predict",
                entity,
                String.class
            );
            if (json == null || json.isBlank()) {
                return Map.of("message", "empty response");
            }
            JsonNode root = MAPPER.readTree(json);
            return MAPPER.convertValue(root, Map.class);
        } catch (RestClientException ex) {
            log.warn("glucose prediction service unavailable: {}", ex.getMessage());
            return Map.of("message", ex.getMessage(), "risk_level", "unknown");
        } catch (Exception ex) {
            log.warn("glucose prediction parse failed: {}", ex.getMessage());
            return Map.of("message", ex.getMessage(), "risk_level", "unknown");
        }
    }
}
