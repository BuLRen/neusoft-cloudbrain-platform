package com.xikang.pharmacy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 经 gateway-service 转发至 payment-service 内部 API。
 */
@FeignClient(name = "payment-service", url = "${services.payment-service.url}")
public interface PaymentFeignClient {

    @PostMapping("/api/payment/internal/items")
    Map<String, Object> createItem(@RequestBody Map<String, Object> body);

    @GetMapping("/api/payment/internal/items/by-register")
    Map<String, Object> getItemByRegister(@RequestParam("registerId") Long registerId,
                                          @RequestParam("itemCode") String itemCode);
}
