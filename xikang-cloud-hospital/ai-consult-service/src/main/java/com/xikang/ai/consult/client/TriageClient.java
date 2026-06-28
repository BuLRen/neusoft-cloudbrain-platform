package com.xikang.ai.consult.client;

import com.xikang.ai.consult.client.dto.TriageSummary;
import com.xikang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ai-triage-service 的 Feign 客户端。
 *
 * <p>用途：预问诊开始时按 {@code registerId} 反查导诊小结，把导诊阶段已采集到的
 * 症状原文、推荐科室、AI 分析等注入到预问诊 prompt，实现"导诊 → 预问诊"上下文串联。
 *
 * <p>采用 url 直连模式（与 registration-service 的 FeignClient 风格一致），
 * 无需引入 spring-cloud-starter-loadbalancer。url 通过配置项 {@code triage.service.url}
 * 注入，默认 {@code http://localhost:8101}（对应 ai-triage-service 的端口）。
 *
 * <p>对应后端契约：{@code AiTriageController#getTriageSummaryByRegisterId}
 * ({@code GET /api/ai/triage/summary/register/{registerId}})。
 */
@FeignClient(name = "ai-triage-service", path = "/api/ai/triage",
        url = "${triage.service.url:http://localhost:8101}")
public interface TriageClient {

    /**
     * 按 registerId 反查导诊小结。
     *
     * @param registerId 挂号 ID
     * @return 导诊小结；若该挂号未做过导诊，data 为 null
     */
    @GetMapping("/summary/register/{registerId}")
    Result<TriageSummary> getTriageSummary(@PathVariable("registerId") Integer registerId);

    /**
     * 按 patientId 反查导诊小结（registerId 查不到时的兜底入口）。
     *
     * @param patientId 患者 ID
     * @return 导诊小结；若该患者未做过导诊，data 为 null
     */
    @GetMapping("/summary/patient/{patientId}")
    Result<TriageSummary> getTriageSummaryByPatientId(@PathVariable("patientId") Long patientId);
}
