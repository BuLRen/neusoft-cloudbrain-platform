package com.xikang.schedule.service;

import com.xikang.schedule.dto.AiGeneratePlanRequest;
import com.xikang.schedule.dto.AiGeneratePlanResult;
import com.xikang.schedule.dto.AiGenerateTaskView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AI 排班异步任务管理。
 * <p>状态存内存 ConcurrentHashMap，启动时启动一个清理线程清除完成超过 5 分钟的条目。</p>
 */
@Slf4j
@Service
public class AiGenerateTaskService {

    /** 状态：运行中 */
    public static final String STATUS_RUNNING = "running";
    /** 状态：成功 */
    public static final String STATUS_SUCCESS = "success";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "failed";
    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "cancelled";

    private final ConcurrentHashMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "ai-generate-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ai-generate-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    private final CozeIntegrationService cozeIntegrationService;

    public AiGenerateTaskService(CozeIntegrationService cozeIntegrationService) {
        this.cozeIntegrationService = cozeIntegrationService;
    }

    @PostConstruct
    public void startCleaner() {
        cleaner.scheduleAtFixedRate(this::cleanupFinished, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        cleaner.shutdownNow();
    }

    /**
     * 提交任务。如果同 key 已经在跑，直接返回那个任务视图。
     */
    public AiGenerateTaskView submit(AiGeneratePlanRequest request) {
        String key = buildKey(request);
        TaskRecord existing = tasks.get(key);
        if (existing != null && STATUS_RUNNING.equals(existing.view.getStatus())) {
            return existing.view;
        }

        TaskRecord record = new TaskRecord();
        record.key = key;
        record.view = newView(STATUS_RUNNING, "validating", 5, "任务已提交，准备开始");
        record.cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        tasks.put(key, record);

        executor.submit(() -> runTask(record, request));
        return record.view;
    }

    /**
     * 取任务视图，没有则返回 null。
     */
    public AiGenerateTaskView getActive(Long operatorId, Long departmentId, String month) {
        if (operatorId == null || departmentId == null || month == null) {
            return null;
        }
        TaskRecord record = tasks.get(buildKey(operatorId, departmentId, month));
        return record == null ? null : record.view;
    }

    /**
     * 取消任务，返回是否真的有运行中任务被取消。
     */
    public boolean cancel(Long operatorId, Long departmentId, String month) {
        if (operatorId == null || departmentId == null || month == null) {
            return false;
        }
        TaskRecord record = tasks.get(buildKey(operatorId, departmentId, month));
        if (record == null) {
            return false;
        }
        if (!STATUS_RUNNING.equals(record.view.getStatus())) {
            return false;
        }
        record.cancelled.set(true);
        if (record.disposable != null) {
            record.disposable.dispose();
        }
        record.view.setStatus(STATUS_CANCELLED);
        record.view.setMessage("任务已取消");
        record.view.setUpdatedAt(System.currentTimeMillis());
        return true;
    }

    private void runTask(TaskRecord record, AiGeneratePlanRequest request) {
        Consumer<CozeIntegrationService.StageProgress> sink = progress -> {
            if (record.cancelled.get()) {
                throw new RuntimeException("__TASK_CANCELLED__");
            }
            record.view.setStage(progress.stage());
            record.view.setPercent(progress.percent());
            record.view.setMessage(progress.message());
            record.view.setUpdatedAt(System.currentTimeMillis());
        };

        try {
            // Coze 阶段需要拿到 disposable 才能取消
            // 改用包一层：在 orchestrate 之外取 disposable 的方式不可行，
            // 因此这里通过自定义 sink 抛 RuntimeException 中断；但更稳妥的是在 orchestrate 内部
            // 给 WebClient 加 takeUntil。由于 takeUntil 需要外部信号，简化做法：靠 sink 内 cancelled
            // 抛异常让 Flux 终止（Flux 报错会自动 dispose 链）。
            AiGeneratePlanResult result = cozeIntegrationService.orchestrate(request, sink);
            if (record.cancelled.get()) {
                markCancelled(record);
                return;
            }
            cozeIntegrationService.persistAiPlanAndSchedules(request, result, sink);
            if (record.cancelled.get()) {
                markCancelled(record);
                return;
            }
            record.view.setStatus(STATUS_SUCCESS);
            record.view.setStage("done");
            record.view.setPercent(100);
            record.view.setMessage(result.getMessage() != null ? result.getMessage() : "排班方案已生成");
            record.view.setPlanId(result.getPlanId());
            record.view.setUpdatedAt(System.currentTimeMillis());
            log.info("AI 排班生成完成 taskKey={} planId={} count={}",
                    record.key, result.getPlanId(), result.getScheduleCount());
        } catch (RuntimeException ex) {
            if (record.cancelled.get() || ex.getMessage() != null && ex.getMessage().contains("__TASK_CANCELLED__")) {
                markCancelled(record);
                return;
            }
            log.error("AI 排班生成失败 taskKey={}", record.key, ex);
            record.view.setStatus(STATUS_FAILED);
            record.view.setStage("error");
            record.view.setPercent(0);
            record.view.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            record.view.setMessage("生成失败：" + record.view.getError());
            record.view.setUpdatedAt(System.currentTimeMillis());
        } finally {
            // 给前端留出查看成功/失败横幅的时间
            scheduleExpiry(record);
        }
    }

    private void markCancelled(TaskRecord record) {
        record.view.setStatus(STATUS_CANCELLED);
        record.view.setStage("cancelled");
        record.view.setMessage("任务已取消");
        record.view.setUpdatedAt(System.currentTimeMillis());
        scheduleExpiry(record);
    }

    private void scheduleExpiry(TaskRecord record) {
        // 5 分钟后由清理线程回收
        cleaner.schedule(() -> tasks.remove(record.key), 5, TimeUnit.MINUTES);
    }

    private void cleanupFinished() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (TaskRecord record : tasks.values()) {
            String status = record.view.getStatus();
            if (STATUS_RUNNING.equals(status)) {
                continue;
            }
            Long updated = record.view.getUpdatedAt();
            if (updated != null && now - updated > TimeUnit.MINUTES.toMillis(5)) {
                tasks.remove(record.key);
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("AI 排班任务清理完成，移除 {} 个过期条目", removed);
        }
    }

    private String buildKey(AiGeneratePlanRequest request) {
        return buildKey(request.getOperatorId(), request.getDepartmentId(), request.getMonth());
    }

    private String buildKey(Long operatorId, Long departmentId, String month) {
        return operatorId + ":" + departmentId + ":" + month;
    }

    private AiGenerateTaskView newView(String status, String stage, int percent, String message) {
        AiGenerateTaskView view = new AiGenerateTaskView();
        long now = System.currentTimeMillis();
        view.setStatus(status);
        view.setStage(stage);
        view.setPercent(percent);
        view.setMessage(message);
        view.setCreatedAt(now);
        view.setUpdatedAt(now);
        return view;
    }

    /**
     * 任务记录：状态视图 + 取消信号 + 可选 disposable（保留扩展位）。
     */
    private static class TaskRecord {
        String key;
        AiGenerateTaskView view;
        java.util.concurrent.atomic.AtomicBoolean cancelled;
        // 当前没用上：WebClient 取消需要包 takeUntil；保留以便后续升级
        @SuppressWarnings("unused")
        reactor.core.Disposable disposable;
    }
}
