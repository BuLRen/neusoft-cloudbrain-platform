package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.MedtechService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MedTech Controller
 */
@RestController
@RequestMapping("/api/medtech")
@RequiredArgsConstructor
public class MedtechController {

    private final MedtechService medtechService;

    /**
     * Get examination list by registration ID
     */
    @GetMapping("/examination/{registrationId}")
    public Result<Object> getExaminationList(@PathVariable Long registrationId) {
        return Result.success(medtechService.getExaminationList(registrationId));
    }

    /**
     * Create examination order
     */
    @PostMapping("/examination")
    public Result<Map<String, Object>> createExamination(@RequestBody Map<String, Object> examinationRequest) {
        Map<String, Object> result = medtechService.createExamination(examinationRequest);
        return Result.success(result);
    }

    /**
     * Get examination report
     */
    @GetMapping("/report/{examinationId}")
    public Result<Map<String, Object>> getExaminationReport(@PathVariable Long examinationId) {
        return Result.success(medtechService.getExaminationReport(examinationId));
    }
}
