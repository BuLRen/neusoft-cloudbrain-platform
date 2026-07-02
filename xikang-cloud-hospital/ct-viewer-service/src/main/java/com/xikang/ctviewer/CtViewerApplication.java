package com.xikang.ctviewer;

import com.xikang.common.env.EnvLoader;
import com.xikang.common.exception.GlobalExceptionHandler;
import com.xikang.ctviewer.config.CtViewerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CtViewerProperties.class)
@Import(GlobalExceptionHandler.class)
public class CtViewerApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(CtViewerApplication.class, args);
    }
}
