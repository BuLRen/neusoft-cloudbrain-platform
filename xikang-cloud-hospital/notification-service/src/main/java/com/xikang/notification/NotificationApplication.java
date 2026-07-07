package com.xikang.notification;

import com.xikang.common.env.EnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消息通知微服务
 * <p>统一承载患者/医生/管理员三方消息（医生变更、请假审批、缴费提醒等）。
 * <p>对外提供 REST API（用户读消息）+ 内部 send API（其他业务服务写入消息）。
 */
@SpringBootApplication
@MapperScan("com.xikang.notification.mapper")
public class NotificationApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(NotificationApplication.class, args);
    }
}
