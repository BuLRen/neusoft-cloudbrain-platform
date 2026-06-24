package com.xikang.pharmacy;

import com.xikang.common.env.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Pharmacy Service Application
 */
@SpringBootApplication
public class PharmacyApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(PharmacyApplication.class, args);
    }
}
