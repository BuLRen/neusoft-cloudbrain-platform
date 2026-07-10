package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 医生工作站排班 Feign 客户端
 * 供 physician-service 调用 schedule-service
 */
@FeignClient(name = "schedule-service", contextId = "physician-schedule-client")
public interface PhysicianScheduleFeignClient {

    /**
     * 获取某医生的排班
     */
    @GetMapping("/api/schedule/physician/{physicianId}")
    List<Map<String, Object>> getByPhysician(
            @PathVariable("physicianId") Long physicianId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate);

    /**
     * 创建请假申请
     */
    @PostMapping("/api/schedule/leave/create")
    Map<String, Object> createLeave(@RequestBody Map<String, Object> body);

    /**
     * 查询请假记录
     */
    @GetMapping("/api/schedule/leave/list")
    List<Map<String, Object>> getLeaves(@RequestParam("physicianId") Long physicianId);

    /**
     * 查询可用替班医生
     */
    @GetMapping("/api/schedule/substitutes")
    List<Map<String, Object>> getSubstitutes(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("leaveDate") String leaveDate,
            @RequestParam("timeSlot") String timeSlot,
            @RequestParam("excludePhysicianId") Long excludePhysicianId);
}