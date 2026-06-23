package com.xikang.physician;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Physician Service Application
 */
@SpringBootApplication
@EnableAsync
public class PhysicianApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhysicianApplication.class, args);
    }
}
