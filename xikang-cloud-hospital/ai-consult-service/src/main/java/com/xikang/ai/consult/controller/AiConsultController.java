package com.xikang.ai.consult.controller;

import com.xikang.ai.consult.entity.AiPreVisitRecord;
import com.xikang.ai.consult.service.AiConsultService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Consult Controller - AI预问诊控制器
 */
@RestController
@RequestMapping("/api/ai/consult")
@RequiredArgsConstructor
public class AiConsultController {

    private final AiConsultService aiConsultService;

    /**
     * 预问诊
     */
    @PostMapping("/previsit")
    public Result<Map<String, Object>> previsit(@RequestBody Map<String, Object> patientInfo) {
        Map<String, Object> result = aiConsultService.previsit(patientInfo);
        return Result.success(result);
    }

    /**
     * 生成预问诊摘要
     */
    @PostMapping("/summary")
    public Result<Map<String, Object>> generateSummary(@RequestBody Map<String, Object> context) {
        Map<String, Object> result = aiConsultService.generateSummary(context);
        return Result.success(result);
    }

    /**
     * 预问诊对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> chatRequest) {
        Map<String, Object> response = aiConsultService.chat(chatRequest);
        return Result.success(response);
    }

    /**
     * 获取预问诊记录
     */
    @GetMapping("/record/{id}")
    public Result<AiPreVisitRecord> getPreVisitRecord(@PathVariable Long id) {
        AiPreVisitRecord record = aiConsultService.getPreVisitRecord(id);
        return Result.success(record);
    }

    /**
     * 按挂号ID获取预问诊记录
     */
    @GetMapping("/record/register/{registerId}")
    public Result<AiPreVisitRecord> getPreVisitRecordByRegisterId(@PathVariable Long registerId) {
        AiPreVisitRecord record = aiConsultService.getPreVisitRecordByRegisterId(registerId);
        return Result.success(record);
    }
}
