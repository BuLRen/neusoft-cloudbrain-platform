package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.calling.RegistrationCallingClient;
import com.xikang.physician.context.PhysicianAuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 医生工作站"叫号"接口（设计文档 §4.1）。
 *
 * 5 个接口，都从 PhysicianAuthContext 取当前医生的 employeeId：
 *   POST /api/physician/call/next                    叫下一个
 *   POST /api/physician/call/{registerId}            指定叫号
 *   POST /api/physician/call/{registerId}/answer     患者应答
 *   POST /api/physician/call/{registerId}/pass       标记过号
 *   GET  /api/physician/call/current                 当前叫号
 *
 * 实现方式：通过 RegistrationCallingClient HTTP 调 registration-service 的 internal 接口。
 * 医生身份（employeeId）从 token 解析后透传给 registration-service。
 */
@RestController
@RequestMapping("/api/physician/call")
public class PhysicianCallController {

    private final RegistrationCallingClient client;

    public PhysicianCallController(RegistrationCallingClient client) {
        this.client = client;
    }

    /** 叫下一个 */
    @PostMapping("/next")
    public Result<Map<String, Object>> callNext() {
        Long employeeId = PhysicianAuthContext.employeeIdOrNull();
        if (employeeId == null) {
            return Result.error("无法识别当前医生身份");
        }
        Map<String, Object> result = client.callNext(employeeId);
        return wrapResult(result);
    }

    /** 指定叫号（重叫过号常用） */
    @PostMapping("/{registerId}")
    public Result<Map<String, Object>> callSpecific(@PathVariable Long registerId) {
        Long employeeId = PhysicianAuthContext.employeeIdOrNull();
        Map<String, Object> result = client.callSpecific(registerId, employeeId);
        return wrapResult(result);
    }

    /** 患者应答（进诊室） */
    @PostMapping("/{registerId}/answer")
    public Result<Map<String, Object>> answer(@PathVariable Long registerId) {
        Map<String, Object> result = client.answer(registerId);
        return wrapResult(result);
    }

    /** 标记过号 */
    @PostMapping("/{registerId}/pass")
    public Result<Map<String, Object>> pass(@PathVariable Long registerId) {
        Map<String, Object> result = client.pass(registerId);
        return wrapResult(result);
    }

    /** 查当前叫号 */
    @GetMapping("/current")
    public Result<Map<String, Object>> current() {
        Long employeeId = PhysicianAuthContext.employeeIdOrNull();
        if (employeeId == null) {
            return Result.success(Map.of("hasCalling", false));
        }
        Map<String, Object> result = client.currentCalling(employeeId);
        return wrapResult(result);
    }

    /**
     * 把 RegistrationCallingClient 返回的 Map 包成 Result。
     * - Map 含 "code"=200 → 取其他字段作为 data
     * - Map 含 "code"!=200 → 把 message 作为错误返回
     */
    private Result<Map<String, Object>> wrapResult(Map<String, Object> result) {
        Object codeObj = result.get("code");
        int code = codeObj instanceof Number n ? n.intValue() : 200;
        if (code == 200) {
            // 移除冗余的 code 字段
            Map<String, Object> data = new java.util.LinkedHashMap<>(result);
            data.remove("code");
            data.remove("message");
            return Result.success(data);
        }
        return Result.error(String.valueOf(result.getOrDefault("message", "叫号操作失败")));
    }
}
