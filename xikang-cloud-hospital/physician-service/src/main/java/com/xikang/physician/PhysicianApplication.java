package com.xikang.physician;

import com.xikang.common.env.EnvLoader;
import com.xikang.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Physician Service Application
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@EnableAsync
@EnableFeignClients
public class PhysicianApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(PhysicianApplication.class, args);
    }
}
