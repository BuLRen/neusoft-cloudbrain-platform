package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 排班服务 Feign 客户端
 *供 registration-service 调用 schedule-service
 */
@FeignClient(name = "schedule-service", url = "${schedule.service.url:http://localhost:8095}")
public interface ScheduleFeignClient {

    /**
     * 获取可用排班
     */
    @GetMapping("/api/schedule/available")
    Map<String, Object> getAvailable(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("date") String date);

    /**
     * 获取日期范围的可用排班
     */
    @GetMapping("/api/schedule/available/range")
    Map<String, Object> getAvailableByDateRange(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate);

    /**
     * 获取排班详情
     */
    @GetMapping("/api/schedule/detail/{scheduleId}")
    Map<String, Object> getDetail(@PathVariable("scheduleId") Long scheduleId);

    /**
     * 扣减号源
     */
    @PostMapping("/api/schedule/quota/deduct")
    Map<String, Object> deductQuota(@RequestBody Map<String, Object> body);

    /**
     * 退还号源
     */
    @PostMapping("/api/schedule/quota/return")
    Map<String, Object> returnQuota(@RequestBody Map<String, Object> body);

    /**
     * 查询剩余号源
     */
    @GetMapping("/api/schedule/quota/{scheduleId}")
    Map<String, Object> getQuota(@PathVariable("scheduleId") Long scheduleId);
}