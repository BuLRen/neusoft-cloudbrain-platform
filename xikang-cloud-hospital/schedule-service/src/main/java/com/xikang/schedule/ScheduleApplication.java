package com.xikang.schedule;

import com.xikang.common.env.EnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.xikang.schedule.mapper")
@EnableFeignClients
public class ScheduleApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(ScheduleApplication.class, args);
    }
}
