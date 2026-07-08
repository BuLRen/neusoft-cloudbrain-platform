package com.xikang.ctviewer.config;

import com.xikang.ctviewer.config.CtViewerProperties.AiCt;
import com.xikang.ctviewer.config.CtViewerProperties.Algo;
import com.xikang.ctviewer.config.CtViewerProperties.LungNodule;
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

    @Bean
    public RestTemplate aiCtRestTemplate(RestTemplateBuilder builder, CtViewerProperties properties) {
        AiCt aiCt = properties.getAiCt();
        return builder
            .setConnectTimeout(Duration.ofMillis(aiCt.getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(aiCt.getReadTimeoutMs()))
            .build();
    }

    @Bean
    public RestTemplate lungNoduleSegRestTemplate(RestTemplateBuilder builder, CtViewerProperties properties) {
        LungNodule lungNodule = properties.getLungNodule();
        return builder
            .setConnectTimeout(Duration.ofMillis(lungNodule.getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(lungNodule.getReadTimeoutMs()))
            .build();
    }
}
