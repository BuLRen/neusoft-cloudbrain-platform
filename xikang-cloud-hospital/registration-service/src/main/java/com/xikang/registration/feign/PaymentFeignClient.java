package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Payment Service Feign 客户端（v3.2：registration-service 调 payment-service 内部 API）
 *
 * 用途：
 *   - createRegistration 时建 REGISTRATION_FEE 行
 *   - tryBalancePayment 时扣款
 *   - cancelRegistration / RefundService 时退款
 *   - ExpenseRecordService / StatsMapper 改 Feign 透传
 *   - ChargeService.charge 收费员集中收费申请 write-token
 */
@FeignClient(name = "payment-service", url = "${services.payment-service.url}")
public interface PaymentFeignClient {

    // ============== createItem ==============

    @PostMapping("/api/payment/internal/items")
    Map<String, Object> createItem(@RequestBody Map<String, Object> body);

    // ============== query ==============

    @GetMapping("/api/payment/internal/items")
    Map<String, Object> getItems(@RequestParam("registerId") Long registerId,
                                 @RequestParam(value = "itemCode", required = false) String itemCode,
                                 @RequestParam(value = "status", required = false) Integer status);

    @GetMapping("/api/payment/internal/items/by-register")
    Map<String, Object> getItemByRegister(@RequestParam("registerId") Long registerId,
                                          @RequestParam("itemCode") String itemCode);

    @GetMapping("/api/payment/internal/items/summary")
    Map<String, Object> summary(@RequestParam("registerId") Long registerId);

    @GetMapping("/api/payment/internal/records")
    Map<String, Object> records(@RequestParam(value = "patientId", required = false) Long patientId,
                                @RequestParam(value = "registerId", required = false) Long registerId,
                                @RequestParam(value = "status", required = false) Integer status,
                                @RequestParam(value = "startDate", required = false) LocalDate startDate,
                                @RequestParam(value = "endDate", required = false) LocalDate endDate);

    @GetMapping("/api/payment/internal/stats/daily-charges")
    Map<String, Object> dailyCharges(@RequestParam("startDate") LocalDate startDate,
                                     @RequestParam("endDate") LocalDate endDate);

    @GetMapping("/api/payment/internal/admin/orders")
    Map<String, Object> listAdminOrders(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "patientId", required = false) Long patientId,
                                        @RequestParam(value = "status", required = false) Integer status,
                                        @RequestParam(value = "startDate", required = false) LocalDate startDate,
                                        @RequestParam(value = "endDate", required = false) LocalDate endDate,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/payment/internal/admin/orders/{registerId}")
    Map<String, Object> getAdminOrderDetail(@PathVariable("registerId") Long registerId);

    // ============== pay / refund ==============

    @PostMapping("/api/payment/internal/items/{itemId}/pay")
    Map<String, Object> payItem(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    @PostMapping("/api/payment/internal/items/{itemId}/refund")
    Map<String, Object> refund(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    // ============== write-token ==============

    @PostMapping("/api/payment/internal/write-token")
    Map<String, Object> acquireWriteToken(@RequestBody Map<String, Object> body);

    @DeleteMapping("/api/payment/internal/write-token")
    Map<String, Object> releaseWriteToken(@RequestBody Map<String, Object> body);
}
