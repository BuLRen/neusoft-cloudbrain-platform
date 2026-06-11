package com.xikang.ai.triage;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Triage Service Application
 */
@SpringBootApplication
public class AiTriageApplication {

    public static void main(String[] args) {
        // 加载 .env 文件到系统属性（让 Spring @Value 能读取）
        EnvLoader.load();
        SpringApplication.run(AiTriageApplication.class, args);
    }
}