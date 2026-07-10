package com.xikang.pharmacy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * pharmacy-service 调用 ai-pharmacy-service。
 * <p>url 留空时走 Nacos 服务发现；本地单机调试可配置 services.ai-pharmacy.url=http://localhost:8104
 */
@FeignClient(name = "ai-pharmacy-service", url = "${services.ai-pharmacy.url:}")
public interface AiPharmacyFeignClient {

    @PostMapping("/api/ai/pharmacy/followup")
    Map<String, Object> createFollowUpPlan(@RequestBody Map<String, Object> request);

    @PostMapping("/api/ai/pharmacy/guide")
    Map<String, Object> getMedicationGuide(@RequestBody Map<String, Object> drugInfo);

    @PostMapping("/api/ai/pharmacy/medication-guide")
    Map<String, Object> generateMedicationGuide(@RequestBody Map<String, Object> ctx);

    @PostMapping("/api/ai/pharmacy/review")
    Map<String, Object> reviewPrescription(@RequestBody Map<String, Object> prescription);

    @GetMapping("/api/ai/pharmacy/followup/patient/{patientId}")
    Map<String, Object> getPatientFollowUpPlans(@PathVariable("patientId") Long patientId);

    @PostMapping("/api/ai/pharmacy/followup/{planId}/feedback")
    Map<String, Object> submitFollowUpFeedback(@PathVariable("planId") Long planId,
                                               @RequestBody Map<String, Object> feedback);
}
