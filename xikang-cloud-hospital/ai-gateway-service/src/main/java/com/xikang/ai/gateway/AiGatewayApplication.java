package com.xikang.ai.gateway;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Gateway Application
 */
@SpringBootApplication
public class AiGatewayApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
