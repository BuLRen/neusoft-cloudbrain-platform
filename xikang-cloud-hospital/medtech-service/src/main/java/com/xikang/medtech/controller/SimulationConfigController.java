package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.dto.SimulationConfigSaveRequest;
import com.xikang.medtech.service.SimulationConfigAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/simulation-configs")
@RequiredArgsConstructor
public class SimulationConfigController {

    private final SimulationConfigAdminService simulationConfigAdminService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return Result.success(simulationConfigAdminService.listAll(keyword));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Integer id) {
        return simulationConfigAdminService.getDetail(id)
            .map(Result::success)
            .orElseGet(() -> Result.error("配置不存在"));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody SimulationConfigSaveRequest request) {
        try {
            return Result.success("模拟配置创建成功", simulationConfigAdminService.create(request));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(
        @PathVariable Integer id,
        @RequestBody SimulationConfigSaveRequest request
    ) {
        try {
            return Result.success("模拟配置更新成功", simulationConfigAdminService.update(id, request));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        try {
            simulationConfigAdminService.delete(id);
            return Result.success("模拟配置已删除", null);
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }
}
