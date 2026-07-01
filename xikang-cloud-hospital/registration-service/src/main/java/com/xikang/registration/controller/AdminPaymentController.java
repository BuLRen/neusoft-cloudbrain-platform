package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/payment-orders")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping("/patients/search")
    public Result<List<Map<String, Object>>> searchPatients(
        @RequestParam String keyword,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        return Result.success(adminPaymentService.searchPatients(keyword, limit));
    }

    @GetMapping("/patients/{patientId}/balance")
    public Result<Map<String, Object>> getPatientBalance(@PathVariable Integer patientId) {
        return Result.success(adminPaymentService.getPatientBalance(patientId));
    }

    @PostMapping("/patients/{patientId}/recharge")
    public Result<Map<String, Object>> rechargePatient(
        @PathVariable Integer patientId,
        @RequestBody Map<String, Object> body
    ) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        Long operatorId = body.get("operatorId") != null
            ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body.get("operatorName") != null
            ? String.valueOf(body.get("operatorName")) : null;
        return Result.success(adminPaymentService.rechargePatient(
            patientId, amount, remark, operatorId, operatorName
        ));
    }

    @PostMapping("/charge")
    public Result<Map<String, Object>> markItemsPaid(@RequestBody Map<String, Object> body) {
        Long registerId = Long.valueOf(body.get("registerId").toString());
        Object rawItemIds = body.get("itemIds");
        List<?> itemIds = rawItemIds instanceof List<?> list ? list : null;
        Long operatorId = body.get("operatorId") != null
            ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body.get("operatorName") != null
            ? String.valueOf(body.get("operatorName")) : null;
        return Result.success(adminPaymentService.markItemsPaid(
            registerId, itemIds, operatorId, operatorName
        ));
    }

    @GetMapping
    public Result<Map<String, Object>> listPaymentOrders(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminPaymentService.listPaymentOrders(
            keyword, patientId, status, startDate, endDate, page, size
        ));
    }

    @PostMapping("/{registerId}/items/{itemId}/pay")
    public Result<Map<String, Object>> payItemByBalance(
        @PathVariable Long registerId,
        @PathVariable Long itemId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        Long operatorId = body != null && body.get("operatorId") != null
            ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
            ? String.valueOf(body.get("operatorName")) : null;
        return Result.success(adminPaymentService.payItemByBalance(itemId, operatorId, operatorName));
    }

    @PostMapping("/{registerId}/pay-all")
    public Result<Map<String, Object>> payAllByBalance(
        @PathVariable Long registerId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        Long operatorId = body != null && body.get("operatorId") != null
            ? Long.valueOf(body.get("operatorId").toString()) : null;
        String operatorName = body != null && body.get("operatorName") != null
            ? String.valueOf(body.get("operatorName")) : null;
        return Result.success(adminPaymentService.payAllByBalance(registerId, operatorId, operatorName));
    }

    @GetMapping("/{registerId}")
    public Result<Map<String, Object>> getPaymentOrderDetail(@PathVariable Long registerId) {
        return Result.success(adminPaymentService.getPaymentOrderDetail(registerId));
    }
}
