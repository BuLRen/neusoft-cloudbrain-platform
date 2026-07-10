package com.xikang.payment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Registration Service Feign 客户端（v3.2：支付成功后回调通知 registration 更新 register 状态）
 *
 * 回调路径 /api/registration/internal/{registerId}/on-fee-paid 由本次改造新增（registration-service §5.2）。
 */
@FeignClient(name = "registration-service")
public interface RegistrationFeignClient {

    /**
     * 支付成功后通知 registration-service，由其重新汇总并决定是否更新 visit_state / pay_status。
     * 回调本身幂等（registration 端重算 summary）— v3.2 §5.2。
     */
    @PostMapping("/api/registration/internal/{registerId}/on-fee-paid")
    Map<String, Object> notifyFeePaid(@PathVariable("registerId") Long registerId, @RequestBody Map<String, Object> body);
}
