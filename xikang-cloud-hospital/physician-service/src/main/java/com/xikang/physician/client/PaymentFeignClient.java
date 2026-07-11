package com.xikang.physician.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 直连 payment-service 内部出账/校验 API（不经 gateway）。
 * 地址由 services.payment-service.url / PAYMENT_SERVICE_URL 配置，默认同机 127.0.0.1:8096。
 */
@FeignClient(name = "payment-service", url = "${services.payment-service.url}")
public interface PaymentFeignClient {

    @PostMapping("/api/payment/internal/items")
    Map<String, Object> createItem(@RequestBody Map<String, Object> body);

    @GetMapping("/api/payment/internal/check-paid/register/{registerId}")
    Map<String, Object> checkPaidByRegister(@PathVariable("registerId") Long registerId);
}
