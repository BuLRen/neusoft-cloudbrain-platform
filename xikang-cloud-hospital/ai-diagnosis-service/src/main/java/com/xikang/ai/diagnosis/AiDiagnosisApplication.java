package com.xikang.ai.diagnosis;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Diagnosis Service Application
 */
@SpringBootApplication
public class AiDiagnosisApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiDiagnosisApplication.class, args);
    }
}
