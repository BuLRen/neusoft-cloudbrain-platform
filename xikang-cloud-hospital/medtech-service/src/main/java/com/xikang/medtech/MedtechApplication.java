package com.xikang.medtech;

import com.xikang.common.exception.GlobalExceptionHandler;
import com.xikang.medtech.ai.DifyAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * MedTech Service Application
 */
@SpringBootApplication
@EnableConfigurationProperties(DifyAiProperties.class)
@Import(GlobalExceptionHandler.class)
public class MedtechApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedtechApplication.class, args);
    }
}
