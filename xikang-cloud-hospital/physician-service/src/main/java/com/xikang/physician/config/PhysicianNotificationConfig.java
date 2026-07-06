package com.xikang.physician.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PhysicianNotificationConfig {

    /**
     * 专用 RestTemplate for notification-service：超时设短（2s connect / 3s read），
     * 避免 notification-service 慢响应拖垮 physician-service 的开单请求。
     */
    @Bean
    public RestTemplate notificationRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }
}
