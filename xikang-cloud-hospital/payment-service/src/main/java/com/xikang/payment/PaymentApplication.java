package com.xikang.payment;

import com.xikang.common.env.EnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service Application
 *
 * v3.2 设计：接管 expense_record 表的读写主职责。
 * 调用 auth-service 扣余额/退款；回调 registration-service 更新 register 状态。
 */
@SpringBootApplication(scanBasePackages = "com.xikang")
@MapperScan("com.xikang.payment.mapper")
@EnableFeignClients
@EnableScheduling
public class PaymentApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(PaymentApplication.class, args);
    }
}
