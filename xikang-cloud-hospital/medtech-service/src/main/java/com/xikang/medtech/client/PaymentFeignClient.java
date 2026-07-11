package com.xikang.medtech.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 直连 payment-service 内部 API。
 */
@FeignClient(name = "payment-service", url = "${services.payment-service.url}")
public interface PaymentFeignClient {

    @GetMapping("/api/payment/internal/items/batch")
    Map<String, Object> listItemsBatch(@RequestParam("registerIds") List<Long> registerIds,
                                       @RequestParam(value = "itemCodes", required = false) List<String> itemCodes);

    @GetMapping("/api/payment/internal/check-paid/item")
    Map<String, Object> checkPaidByItem(@RequestParam("registerId") Long registerId,
                                          @RequestParam("itemCode") String itemCode,
                                          @RequestParam("sourceId") Long sourceId);
}
