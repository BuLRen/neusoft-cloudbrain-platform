package com.xikang.pharmacy.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Pharmacy Service 配置
 */
@Configuration
public class PharmacyConfig {

    /**
     * 配置带超时的 RestTemplate，供 AiPharmacyClient 使用
     * 连接超时 3s，读取超时 10s（AI 服务可能较慢）
     */
    @Bean
    public RestTemplate aiPharmacyRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}