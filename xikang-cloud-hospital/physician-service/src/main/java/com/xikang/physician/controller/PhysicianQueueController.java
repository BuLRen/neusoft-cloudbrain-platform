package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.calling.RegistrationCallingClient;
import com.xikang.physician.config.PhysicianRequestAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 医生候诊队列：查询与调序。
 */
@RestController
@RequestMapping("/api/physician/queue")
public class PhysicianQueueController {

    private final RegistrationCallingClient client;

    public PhysicianQueueController(RegistrationCallingClient client) {
        this.client = client;
    }

    @GetMapping("/waiting")
    public Result<List<Map<String, Object>>> waiting(
            @RequestAttribute(value = PhysicianRequestAttributes.EMPLOYEE_ID, required = false) Long employeeId) {
        if (employeeId == null) {
            return Result.error("无法识别当前医生身份");
        }
        Map<String, Object> result = client.doctorWaitingQueue(employeeId);
        return wrapListResult(result);
    }

    @PutMapping("/reorder")
    public Result<Void> reorder(
            @RequestAttribute(value = PhysicianRequestAttributes.EMPLOYEE_ID, required = false) Long employeeId,
            @RequestBody Map<String, Object> body) {
        if (employeeId == null) {
            return Result.error("无法识别当前医生身份");
        }
        List<Long> registerIds = parseRegisterIds(body.get("registerIds"));
        Map<String, Object> result = client.reorderQueue(employeeId, registerIds);
        return wrapVoidResult(result);
    }

    @SuppressWarnings("unchecked")
    private Result<List<Map<String, Object>>> wrapListResult(Map<String, Object> result) {
        Object codeObj = result.get("code");
        if (codeObj instanceof Number n && n.intValue() != 200) {
            return Result.error(String.valueOf(result.getOrDefault("message", "查询队列失败")));
        }
        Object data = result.get("data");
        if (data instanceof List<?> list) {
            return Result.success((List<Map<String, Object>>) list);
        }
        return Result.success(List.of());
    }

    private Result<Void> wrapVoidResult(Map<String, Object> result) {
        Object codeObj = result.get("code");
        int code = codeObj instanceof Number n ? n.intValue() : 200;
        if (code != 200) {
            return Result.error(String.valueOf(result.getOrDefault("message", "调整队列失败")));
        }
        return Result.success();
    }

    private List<Long> parseRegisterIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        return ids;
    }
}
