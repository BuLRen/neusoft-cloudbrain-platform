package com.xikang.ai.consult;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Consult Service Application
 */
@SpringBootApplication
public class AiConsultApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiConsultApplication.class, args);
    }
}
