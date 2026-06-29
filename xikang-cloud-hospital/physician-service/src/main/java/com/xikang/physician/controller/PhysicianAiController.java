package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.ai.DifyWorkflowException;
import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.ai.W3AutoTriggerService;
import com.xikang.physician.service.PhysicianService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/physician/ai")
public class PhysicianAiController {

    private final PhysicianAiPipelineService pipelineService;
    private final PhysicianService physicianService;
    private final W3AutoTriggerService w3AutoTriggerService;

    public PhysicianAiController(
        PhysicianAiPipelineService pipelineService,
        PhysicianService physicianService,
        W3AutoTriggerService w3AutoTriggerService
    ) {
        this.pipelineService = pipelineService;
        this.physicianService = physicianService;
        this.w3AutoTriggerService = w3AutoTriggerService;
    }

    @GetMapping("/available-examinations")
    public Result<List<Map<String, Object>>> availableExaminations() {
        return Result.success(pipelineService.getAvailableExaminations());
    }

    @PostMapping("/preliminary-diagnosis")
    public Result<Map<String, Object>> runPreliminaryDiagnosis(@RequestBody Map<String, Object> request) {
        try {
            return Result.success("初步诊断生成完成", pipelineService.runPreliminaryDiagnosis(request));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，当前系统暂不支持，请手工填写初步诊断");
            }
            return Result.error("AI 工作流执行失败，请稍后重试或手工填写初步诊断");
        }
    }

    @PostMapping("/w1/structure")
    public Result<Map<String, Object>> runW1(@RequestBody Map<String, Object> request) {
        return Result.success("W1 病历字段结构化完成", pipelineService.runW1(request));
    }

    @PostMapping("/w2/recommend")
    public Result<Map<String, Object>> runW2(@RequestBody Map<String, Object> request) {
        try {
            Long registerId = toLong(request.get("registerId"));
            if (registerId == null) {
                return Result.error("registerId 不能为空");
            }
            return Result.success("W2 检查推荐完成", pipelineService.runW2(registerId));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，请手工选择检查项目");
            }
            return Result.error("AI 检查推荐失败，请稍后重试或手工开立");
        }
    }

    @PostMapping("/w2b/simulate")
    public Result<Map<String, Object>> runW2b(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        boolean autoCreate = Boolean.TRUE.equals(request.get("autoCreateRequests"));
        return Result.success("W2b 模拟检查结果完成", pipelineService.runW2b(registerId, autoCreate));
    }

    @GetMapping("/w3/status")
    public Result<Map<String, Object>> getW3Status(@RequestParam Long registerId) {
        if (registerId == null) {
            return Result.error("registerId 不能为空");
        }
        return Result.success(pipelineService.getW3Status(registerId));
    }

    @PostMapping("/w3/analyze")
    public Result<Map<String, Object>> runW3(@RequestBody Map<String, Object> request) {
        try {
            Long registerId = toLong(request.get("registerId"));
            if (registerId == null) {
                return Result.error("registerId 不能为空");
            }
            return Result.success("W3 结果解读完成", pipelineService.runW3(registerId));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，当前系统暂不支持，请稍后重试");
            }
            String detail = ex.getMessage();
            if (detail != null && !detail.isBlank() && !detail.contains("请稍后重试")) {
                return Result.error("AI 结果解读失败：" + detail);
            }
            return Result.error("AI 结果解读失败，请稍后重试");
        }
    }

    @PostMapping("/w3/trigger-async")
    public Result<Void> triggerW3Async(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            return Result.error("registerId 不能为空");
        }
        w3AutoTriggerService.triggerW3(registerId);
        return Result.success("W3 异步解读已触发", null);
    }

    @PostMapping("/w4/diagnose")
    public Result<Map<String, Object>> runW4(@RequestBody Map<String, Object> request) {
        try {
            Long registerId = toLong(request.get("registerId"));
            if (registerId == null) {
                return Result.error("registerId 不能为空");
            }
            return Result.success("W4 诊断推理完成", pipelineService.runW4(registerId));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，当前系统暂不支持，请稍后重试");
            }
            String detail = ex.getMessage();
            if (detail != null && !detail.isBlank() && !detail.contains("请稍后重试")) {
                return Result.error("AI 诊断推理失败：" + detail);
            }
            return Result.error("AI 诊断推理失败，请稍后重试");
        }
    }

    @PostMapping("/w5/recommend-drugs")
    public Result<Map<String, Object>> runW5(@RequestBody Map<String, Object> request) {
        try {
            Long registerId = toLong(request.get("registerId"));
            if (registerId == null) {
                return Result.error("registerId 不能为空");
            }
            return Result.success("W5 智能荐药完成", pipelineService.runW5(registerId));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，当前系统暂不支持，请稍后重试");
            }
            String detail = ex.getMessage();
            if (detail != null && !detail.isBlank() && !detail.contains("请稍后重试")) {
                return Result.error("AI 智能荐药失败：" + detail);
            }
            return Result.error("AI 智能荐药失败，请稍后重试");
        }
    }

    @GetMapping("/w5/suggestions/{registerId}")
    public Result<List<Map<String, Object>>> getW5Suggestions(@PathVariable Long registerId) {
        if (registerId == null) {
            return Result.error("registerId 不能为空");
        }
        return Result.success(pipelineService.getDrugSuggestions(registerId));
    }

    @PatchMapping("/w5/suggestions/{id}/adopt")
    public Result<Void> adoptW5Suggestion(@PathVariable Long id) {
        pipelineService.markDrugSuggestionAdopted(id);
        return Result.success("已标记采纳", null);
    }

    @PostMapping("/pipeline/run")
    public Result<Map<String, Object>> runPipeline(@RequestBody Map<String, Object> request) {
        return Result.success("AI 流水线执行完成", pipelineService.runFullPipeline(request));
    }

    @GetMapping("/dify-workflow-contracts")
    public Result<Map<String, Object>> contracts() {
        return Result.success(physicianService.getDifyWorkflowContracts());
    }

    @GetMapping("/ct-model-contract")
    public Result<Map<String, Object>> ctContract() {
        return Result.success(physicianService.getCtModelOutputContract());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }
}
