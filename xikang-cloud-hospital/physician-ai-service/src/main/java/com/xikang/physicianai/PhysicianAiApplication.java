package com.xikang.physicianai;

import com.xikang.common.env.EnvLoader;
import com.xikang.common.exception.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.xikang.physician", "com.xikang.physicianai"})
@Import(GlobalExceptionHandler.class)
@MapperScan({
    "com.xikang.physician.mapper",
    "com.xikang.physician.agent.mapper",
    "com.xikang.physician.copilot.mapper"
})
@EnableFeignClients(basePackages = "com.xikang.physician.feign")
@EnableAsync
public class PhysicianAiApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(PhysicianAiApplication.class, args);
    }
}
