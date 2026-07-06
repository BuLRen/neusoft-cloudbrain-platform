package com.xikang.medtech;

import com.xikang.common.env.EnvLoader;
import com.xikang.common.exception.GlobalExceptionHandler;
import com.xikang.medtech.ai.DifyAiProperties;
import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.config.GlucosePredictionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * MedTech Service Application
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({DifyAiProperties.class, FollowUpProperties.class, GlucosePredictionProperties.class})
@Import(GlobalExceptionHandler.class)
public class MedtechApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(MedtechApplication.class, args);
    }
}
