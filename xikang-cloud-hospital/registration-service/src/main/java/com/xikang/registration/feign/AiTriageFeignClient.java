package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI 导诊服务 Feign 客户端。
 *
 * <p>用途：挂号成功后调用 triage-service 的回填接口，把 register_id 写回该患者最近的导诊记录，
 * 实现"导诊 → 预问诊"上下文串联。预问诊启动时就能按 registerId 反查到导诊摘要。
 *
 * <p>采用 url 直连模式（与 AuthPatientFeignClient 风格一致），无需 LoadBalancer。
 */
@FeignClient(name = "ai-triage-service", url = "${triage.service.url:http://localhost:8101}")
public interface AiTriageFeignClient {

    /**
     * 回填 register_id 到该患者最近一条导诊记录。
     * body: { "patientId": 1, "registerId": 100 }
     */
    @PostMapping("/api/ai/triage/bind-register")
    Map<String, Object> bindRegister(@RequestBody Map<String, Object> body);
}
