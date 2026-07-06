package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.CriticalValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/critical-value")
@RequiredArgsConstructor
public class CriticalValueController {

    private final CriticalValueService criticalValueService;

    @PostMapping("/report")
    public Result<Map<String, Object>> report(@RequestBody Map<String, Object> request) {
        return Result.success("危急值已上报", criticalValueService.report(request));
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending(@RequestParam Long doctorId) {
        return Result.success(criticalValueService.listPending(doctorId));
    }

    @PostMapping("/{id}/ack")
    public Result<Map<String, Object>> acknowledge(@PathVariable Long id) {
        return Result.success("危急值已签收", criticalValueService.acknowledge(id));
    }

    @PostMapping("/{id}/handle")
    public Result<Map<String, Object>> handle(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("危急值已处置", criticalValueService.handle(id, request));
    }

    @GetMapping("/board")
    public Result<Map<String, Object>> board() {
        return Result.success(criticalValueService.board());
    }
}
