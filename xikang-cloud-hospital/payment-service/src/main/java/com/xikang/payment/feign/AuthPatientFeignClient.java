package com.xikang.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 患者账户 Feign 客户端（v3.2：调 auth-service 扣余额 / 退款）
 */
@FeignClient(name = "auth-service", url = "${services.auth-service.url:http://localhost:8081}")
public interface AuthPatientFeignClient {

    @GetMapping("/api/patient/{patientId}")
    Map<String, Object> getPatient(@PathVariable("patientId") Integer patientId);

    @PostMapping("/api/patient/{patientId}/balance/deduct")
    Map<String, Object> deductBalance(@PathVariable("patientId") Integer patientId, @RequestBody Map<String, Object> body);

    @PostMapping("/api/patient/{patientId}/balance/refund")
    Map<String, Object> refundBalance(@PathVariable("patientId") Integer patientId, @RequestBody Map<String, Object> body);
}
