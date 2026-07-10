package com.xikang.ai.consult.client;

import com.xikang.ai.consult.client.dto.TriageSummary;
import com.xikang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ai-triage-service 的 Feign 客户端。
 *
 * <p>用途：预问诊开始时按 {@code sessionId} 精确反查本次就诊对应的导诊小结，
 * 把导诊阶段已采集到的症状原文、推荐科室、AI 分析等注入到预问诊 prompt，
 * 实现"导诊 → 预问诊"上下文串联。
 *
 * <p><b>设计要点</b>：用 sessionId（导诊创建时生成的 UUID）作为串联键，
 * 而不是 registerId/patientId。后者曾导致"猜最近一条"回填错绑，
 * 把患者历史导诊污染进本次预问诊。sessionId 是 1:1 精确绑定，物理上不会错配。
 *
 * <p>走 Nacos 服务发现，由 spring-cloud-starter-loadbalancer 选择实例。
 *
 * <p>对应后端契约：{@code AiTriageController#getTriageSummaryBySessionId}
 * ({@code GET /api/ai/triage/summary/session/{sessionId}})。
 */
@FeignClient(name = "ai-triage-service", path = "/api/ai/triage")
public interface TriageClient {

    /**
     * 按 sessionId 精确反查导诊小结（预问诊的权威查询入口）。
     *
     * @param sessionId 导诊会话 ID
     * @return 导诊小结；sessionId 为空或未做过导诊时 data 为 null（预问诊降级为完整流程）
     */
    @GetMapping("/summary/session/{sessionId}")
    Result<TriageSummary> getTriageSummaryBySessionId(@PathVariable("sessionId") String sessionId);

    /**
     * 按 registerId 反查导诊小结（兜底入口）。
     * 用于预问诊没有 sessionId 时，靠挂号回填（按 sessionId 精确绑定的 register_id）反查。
     * 只要挂号回填成功，这里查到的就是本次导诊。
     *
     * @param registerId 挂号 ID
     * @return 导诊小结；该挂号未关联导诊时 data 为 null
     */
    @GetMapping("/summary/register/{registerId}")
    Result<TriageSummary> getTriageSummaryByRegisterId(@PathVariable("registerId") Integer registerId);
}
