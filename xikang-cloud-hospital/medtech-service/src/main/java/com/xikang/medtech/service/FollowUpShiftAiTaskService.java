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
    private final FollowUpEnrollmentBackfillService enrollmentBackfillService;

    public FollowUpShiftAiTaskService(
        FollowUpShiftDifyService difyService,
        FollowUpShiftScheduleService scheduleService,
        FollowUpEnrollmentBackfillService enrollmentBackfillService
    ) {
        this.difyService = difyService;
        this.scheduleService = scheduleService;
        this.enrollmentBackfillService = enrollmentBackfillService;
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
                record.percent = 20;
                record.message = "同步看诊结束患者到随访池";
                Map<String, Object> backfill = enrollmentBackfillService.backfillDepartmentForPlanning(departmentId);
                int synced = toInt(backfill.get("enrolled"));
                if (synced > 0) {
                    log.info("AI 排班前自动同步随访患者 dept={} synced={}", departmentId, synced);
                }

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
                if (generated.get("patientCount") != null) {
                    saved.put("patientCount", generated.get("patientCount"));
                }
                record.status = STATUS_SUCCESS;
                record.percent = 100;
                int patientCount = toInt(saved.get("patientCount"));
                int taskCount = toInt(saved.get("taskCount"));
                if (FollowUpShiftDifyService.SOURCE_DIFY.equals(source)) {
                    record.message = String.format(
                        "Dify 工作流排班完成，患者池 %d 人，联系任务 %d 个",
                        patientCount,
                        taskCount
                    );
                } else {
                    String note = generated.get("scheduleNote") != null
                        ? String.valueOf(generated.get("scheduleNote"))
                        : "Dify 未启用或调用失败";
                    record.message = String.format(
                        "规则排班完成（%s），患者池 %d 人，联系任务 %d 个",
                        note,
                        patientCount,
                        taskCount
                    );
                }
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

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
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
