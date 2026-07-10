package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI 导诊服务 Feign 客户端。
 *
 * <p>用途：挂号成功后调用 triage-service 的回填接口，按 sessionId 精确把 register_id 写回
 * 本次就诊对应的导诊记录，实现"导诊 → 预问诊"上下文串联。
 *
 * <p><b>设计要点</b>：用 sessionId（前端从导诊页透传过来）作为精确匹配键，
 * 替代旧的"按 patientId 猜最近一条"回填——后者曾把多次导诊/挂号交叉的记录错绑。
 *
 * <p>走 Nacos 服务发现，由 spring-cloud-starter-loadbalancer 选择实例。
 */
@FeignClient(name = "ai-triage-service")
public interface AiTriageFeignClient {

    /**
     * 按 sessionId 精确回填 register_id 到导诊记录。
     * body: { "sessionId": "uuid", "registerId": 100 }
     */
    @PostMapping("/api/ai/triage/bind-register")
    Map<String, Object> bindRegister(@RequestBody Map<String, Object> body);
}
