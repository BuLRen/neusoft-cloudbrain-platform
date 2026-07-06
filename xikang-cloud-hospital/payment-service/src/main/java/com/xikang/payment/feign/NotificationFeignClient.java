package com.xikang.payment.feign;

import com.xikang.payment.feign.dto.PaymentNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Notification Service Feign 客户端
 * <p>payment-service 调用 notification-service 写入消息（支付成功 / 退款成功通知患者）。
 * <p>调用失败时由调用方 try/catch 兜底，绝不能影响支付主流程。
 */
@FeignClient(name = "notification-service", url = "${notification.service.url:http://localhost:8105}")
public interface NotificationFeignClient {

    /**
     * 单条发送（X-Internal-Token 鉴权）
     */
    @PostMapping("/api/notification/send")
    Map<String, Object> send(@RequestBody PaymentNotificationRequest req,
                             @RequestHeader("X-Internal-Token") String token);
}
