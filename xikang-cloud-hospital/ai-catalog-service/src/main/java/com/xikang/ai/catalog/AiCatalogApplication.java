package com.xikang.ai.catalog;

import com.xikang.common.env.EnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lightweight catalog search for Dify W4/W5 HTTP nodes (disease + drug_info).
 */
@SpringBootApplication
@MapperScan("com.xikang.ai.catalog.mapper")
public class AiCatalogApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AiCatalogApplication.class, args);
    }
}
