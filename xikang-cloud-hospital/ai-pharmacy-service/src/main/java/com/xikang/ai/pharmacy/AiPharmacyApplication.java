package com.xikang.ai.pharmacy;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Pharmacy Service Application
 */
@SpringBootApplication
public class AiPharmacyApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiPharmacyApplication.class, args);
    }
}
