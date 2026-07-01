package com.xikang.registration;

import com.xikang.common.env.EnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registration Service Application
 */
@SpringBootApplication(scanBasePackages = "com.xikang")
@MapperScan("com.xikang.registration.mapper")
@EnableFeignClients
@EnableScheduling
public class RegistrationApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(RegistrationApplication.class, args);
    }
}
