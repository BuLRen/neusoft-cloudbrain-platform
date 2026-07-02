package com.xikang.payment.controller;

import com.xikang.common.result.Result;
import com.xikang.payment.service.PaymentService;
import com.xikang.payment.service.WriteTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Payment Controller（v3.2 §4.1 + §4.2）
 *
 * 患者端 API：/api/payment/orders/**
 * 内部 API：/api/payment/internal/**（service-to-service，gateway 不放行；Feign 通过 Nacos 直连）
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final WriteTokenService writeTokenService;

    // ============================================================
    // 患者端 API
    // ============================================================

    @GetMapping("/orders")
    public Result<Map<String, Object>> listOrders(
            @RequestParam Long patientId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(paymentService.listOrders(patientId, status, page, size));
    }

    @GetMapping("/orders/{registerId}")
    public Result<Map<String, Object>> getOrderDetail(@PathVariable Long registerId) {
        return Result.success(paymentService.getOrderDetail(registerId));
    }

    @PostMapping("/orders/{registerId}/items/{itemId}/pay")
    public Result<Map<String, Object>> payItem(@PathVariable Long registerId,
                                               @PathVariable Long itemId,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = body != null && body.get("operatorId") != null
                ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
                ? body.get("operatorName").toString() : "患者自助";
        Map<String, Object> result = paymentService.payItem(itemId, operatorId, operatorName);
        return Result.success(result);
    }

    @PostMapping("/orders/{registerId}/pay-all")
    public Result<Map<String, Object>> payAll(@PathVariable Long registerId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = body != null && body.get("operatorId") != null
                ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
                ? body.get("operatorName").toString() : "患者自助";
        return Result.success(paymentService.payAll(registerId, operatorId, operatorName));
    }

    // ============================================================
    // 内部 API（业务服务 Feign 用）
    // ============================================================

    @PostMapping("/internal/items")
    public Result<Map<String, Object>> createItem(@RequestBody Map<String, Object> body) {
        return Result.success(paymentService.createItem(body));
    }

    @GetMapping("/internal/items")
    public Result<List<Map<String, Object>>> getItems(
            @RequestParam Long registerId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) Integer status) {
        // 简化：复用 queryRecords
        return Result.success(paymentService.queryRecords(null, registerId, status, null, null));
    }

    @GetMapping("/internal/items/by-register")
    public Result<Map<String, Object>> getItemByRegister(
            @RequestParam Long registerId,
            @RequestParam String itemCode,
            @RequestParam(required = false) Long sourceId) {
        return Result.success(paymentService.getItemByRegisterAndSource(registerId, itemCode, sourceId));
    }

    @GetMapping("/internal/check-paid/register/{registerId}")
    public Result<Map<String, Object>> checkPaidByRegister(@PathVariable Long registerId) {
        return Result.success(paymentService.checkPaidByRegister(registerId));
    }

    @GetMapping("/internal/check-paid/item")
    public Result<Map<String, Object>> checkPaidByItem(
            @RequestParam Long registerId,
            @RequestParam String itemCode,
            @RequestParam Long sourceId) {
        return Result.success(paymentService.checkPaidByItem(registerId, itemCode, sourceId));
    }

    @GetMapping("/internal/items/summary")
    public Result<Map<String, Object>> summary(@RequestParam Long registerId) {
        return Result.success(paymentService.summary(registerId));
    }

    @PostMapping("/internal/items/{itemId}/pay")
    public Result<Map<String, Object>> internalPayItem(@PathVariable Long itemId,
                                                       @RequestBody Map<String, Object> body) {
        Long operatorId = body != null && body.get("operatorId") != null
                ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
                ? body.get("operatorName").toString() : "系统";
        return Result.success(paymentService.payItem(itemId, operatorId, operatorName));
    }

    @PostMapping("/internal/items/{itemId}/refund")
    public Result<Map<String, Object>> refund(@PathVariable Long itemId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = body != null && body.get("operatorId") != null
                ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
                ? body.get("operatorName").toString() : "系统";
        String reason = body != null && body.get("reason") != null
                ? body.get("reason").toString() : null;
        return Result.success(paymentService.refund(itemId, operatorId, operatorName, reason));
    }

    @GetMapping("/internal/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return Result.success(paymentService.queryRecords(patientId, registerId, status, startDate, endDate));
    }

    @GetMapping("/internal/stats/daily-charges")
    public Result<List<Map<String, Object>>> dailyCharges(@RequestParam LocalDate startDate,
                                                          @RequestParam LocalDate endDate) {
        return Result.success(paymentService.dailyCharges(startDate, endDate));
    }

    @GetMapping("/internal/admin/orders")
    public Result<Map<String, Object>> listAdminOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(paymentService.listAdminOrders(
            keyword, patientId, status, startDate, endDate, page, size));
    }

    @GetMapping("/internal/admin/orders/{registerId}")
    public Result<Map<String, Object>> getAdminOrderDetail(@PathVariable Long registerId) {
        return Result.success(paymentService.getOrderDetail(registerId));
    }

    // ============================================================
    // write-token（v3.2 §4.2 防双写）
    // ============================================================

    @PostMapping("/internal/write-token")
    public Result<Map<String, Object>> acquireWriteToken(@RequestBody Map<String, Object> body) {
        Long registerId = Long.valueOf(body.get("registerId").toString());
        String holder = body.get("holder").toString();
        int ttl = body.get("ttlSeconds") != null
                ? Integer.parseInt(body.get("ttlSeconds").toString()) : 30;

        boolean ok = writeTokenService.tryAcquire(registerId, holder, ttl);
        if (!ok) {
            String current = writeTokenService.currentHolder(registerId);
            Map<String, Object> conflict = new java.util.LinkedHashMap<>();
            conflict.put("registerId", registerId);
            conflict.put("holder", current);
            return Result.error(409, "该挂号正在被 " + (current != null ? current : "其他流程") + " 处理");
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("registerId", registerId);
        data.put("holder", holder);
        data.put("ttlSeconds", ttl);
        return Result.success("acquired", data);
    }

    @DeleteMapping("/internal/write-token")
    public Result<Void> releaseWriteToken(@RequestBody Map<String, Object> body) {
        Long registerId = Long.valueOf(body.get("registerId").toString());
        String holder = body.get("holder").toString();
        writeTokenService.release(registerId, holder);
        return Result.success();
    }
}
