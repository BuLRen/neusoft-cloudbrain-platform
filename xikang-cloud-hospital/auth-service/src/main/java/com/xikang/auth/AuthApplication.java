package com.xikang.auth;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth Service Application
 */
@SpringBootApplication(scanBasePackages = "com.xikang")
public class AuthApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(AuthApplication.class, args);
    }
}
