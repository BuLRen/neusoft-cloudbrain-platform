package com.xikang.ai.consult;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI Consult Service Application
 */
@SpringBootApplication
@EnableFeignClients
public class AiConsultApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiConsultApplication.class, args);
    }
}
