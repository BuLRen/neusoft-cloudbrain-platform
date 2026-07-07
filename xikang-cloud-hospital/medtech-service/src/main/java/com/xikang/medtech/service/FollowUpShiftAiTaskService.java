package com.xikang.medtech.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class FollowUpShiftAiTaskService {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    private final ConcurrentHashMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "followup-shift-ai");
        t.setDaemon(true);
        return t;
    });

    private final FollowUpShiftDifyService difyService;
    private final FollowUpShiftScheduleService scheduleService;

    public FollowUpShiftAiTaskService(
        FollowUpShiftDifyService difyService,
        FollowUpShiftScheduleService scheduleService
    ) {
        this.difyService = difyService;
        this.scheduleService = scheduleService;
    }

    public Map<String, Object> submit(Long departmentId, String departmentName, String month) {
        String key = departmentId + ":" + month;
        TaskRecord existing = tasks.get(key);
        if (existing != null && STATUS_RUNNING.equals(existing.status)) {
            return existing.toView();
        }

        TaskRecord record = new TaskRecord();
        record.key = key;
        record.status = STATUS_RUNNING;
        record.message = "正在生成排班…";
        record.percent = 10;
        tasks.put(key, record);

        executor.submit(() -> {
            try {
                record.percent = 40;
                record.message = "调用 Dify 工作流 WF-FollowUp-Shift-01";
                Map<String, Object> generated = difyService.generateShifts(departmentId, departmentName, month);
                String source = String.valueOf(generated.getOrDefault("source", "unknown"));
                record.percent = 80;
                record.message = "保存排班数据";
                Map<String, Object> saved = scheduleService.persistGeneratedPlan(
                    departmentId, month, generated, true
                );
                saved.put("source", source);
                record.status = STATUS_SUCCESS;
                record.percent = 100;
                record.message = "dify".equals(source)
                    ? "Dify 工作流排班生成完成"
                    : "规则排班生成完成（Dify 未启用或调用失败）";
                record.result = saved;
            } catch (Exception ex) {
                log.warn("Follow-up shift AI task failed: {}", ex.getMessage());
                record.status = STATUS_FAILED;
                record.message = ex.getMessage();
            }
        });

        return record.toView();
    }

    public Map<String, Object> getActive(Long departmentId, String month) {
        TaskRecord record = tasks.get(departmentId + ":" + month);
        return record != null ? record.toView() : Map.of("status", "idle");
    }

    private static class TaskRecord {
        String key;
        String status;
        String message;
        int percent;
        Map<String, Object> result;

        Map<String, Object> toView() {
            return Map.of(
                "taskKey", key,
                "status", status,
                "message", message != null ? message : "",
                "percent", percent,
                "result", result != null ? result : Map.of()
            );
        }
    }
}
