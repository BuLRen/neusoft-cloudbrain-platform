package com.xikang.ctviewer.config;

import com.xikang.ctviewer.config.CtViewerProperties.Algo;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate ctViewerAlgoRestTemplate(RestTemplateBuilder builder, CtViewerProperties properties) {
        Algo algo = properties.getAlgo();
        return builder
            .setConnectTimeout(Duration.ofMillis(algo.getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(algo.getReadTimeoutMs()))
            .build();
    }
}
