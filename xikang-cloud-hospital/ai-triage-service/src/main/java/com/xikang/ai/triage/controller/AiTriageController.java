package com.xikang.ai.triage.controller;

import com.xikang.ai.triage.dto.TriageRequest;
import com.xikang.ai.triage.dto.TriageSummary;
import com.xikang.ai.triage.entity.AiTriageRecord;
import com.xikang.ai.triage.service.AiTriageService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Triage Controller - AI导诊控制器
 */
@RestController
@RequestMapping("/api/ai/triage")
@RequiredArgsConstructor
public class AiTriageController {

    private final AiTriageService aiTriageService;

    /**
     * 症状分析并推荐科室
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody TriageRequest request) {
        Map<String, Object> result = aiTriageService.analyzeSymptoms(request);
        return Result.success(result);
    }

    /**
     * 获取科室推荐
     */
    @PostMapping("/department")
    public Result<Map<String, Object>> getDepartmentRecommendation(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = aiTriageService.getDepartmentRecommendation(request);
        return Result.success(result);
    }

    /**
     * 导诊对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> chatRequest) {
        Map<String, Object> response = aiTriageService.chat(chatRequest);
        return Result.success(response);
    }

    /**
     * 获取导诊记录
     */
    @GetMapping("/record/{id}")
    public Result<AiTriageRecord> getTriageRecord(@PathVariable Long id) {
        AiTriageRecord record = aiTriageService.getTriageRecord(id);
        return Result.success(record);
    }

    /**
     * 获取患者的导诊记录
     */
    @GetMapping("/records/patient/{patientId}")
    public Result<List<AiTriageRecord>> getPatientTriageRecords(@PathVariable Long patientId) {
        List<AiTriageRecord> records = aiTriageService.getPatientTriageRecords(patientId);
        return Result.success(records);
    }

    /**
     * 按 registerId 反查导诊小结（保留给历史/统计查询用）。
     * 预问诊已改用 sessionId 查询，不再依赖本接口。
     */
    @GetMapping("/summary/register/{registerId}")
    public Result<TriageSummary> getTriageSummaryByRegisterId(@PathVariable Integer registerId) {
        return Result.success(aiTriageService.getTriageSummary(registerId));
    }

    /**
     * 按 sessionId 精确反查导诊小结（预问诊的权威查询入口）。
     *
     * <p>供 ai-consult-service 调用：预问诊开始时，前端把导诊 sessionId 透传过来，
     * 本接口按 sessionId 精确定位本次就诊对应的导诊记录，
     * 杜绝"按 patientId/registerId 猜最近一条"导致的历史导诊污染。
     */
    @GetMapping("/summary/session/{sessionId}")
    public Result<TriageSummary> getTriageSummaryBySessionId(@PathVariable String sessionId) {
        return Result.success(aiTriageService.getTriageSummaryBySessionId(sessionId));
    }

    /**
     * 按 sessionId 精确回填 register_id 到导诊记录。
     * 供 registration-service 在创建挂号成功后调用。
     *
     * <p>body: { "sessionId": "uuid", "registerId": 100 }
     */
    @PostMapping("/bind-register")
    public Result<Boolean> bindRegister(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId") == null ? null : body.get("sessionId").toString();
        Long registerId = toLong(body.get("registerId"));
        boolean ok = aiTriageService.bindRegisterId(sessionId, registerId);
        return Result.success(ok);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
