package com.xikang.registration.controller;

import com.xikang.registration.sse.CallingSubscriberRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 叫号系统 SSE 流端点（设计文档 §5）。
 *
 * 三个正式订阅端点：
 *   GET /api/registration/calling/stream/department/{departmentId}  科室大屏
 *   GET /api/registration/calling/stream/doctor/{doctorId}           报到机/医生工作站
 *   GET /api/registration/calling/stream/global                      全院大屏
 *
 * 每个端点：
 *   1. 创建 SseEmitter(timeout=0，永不超时，靠心跳和客户端断开管理生命周期)
 *   2. 注册到 CallingSubscriberRegistry 对应 topic
 *   3. 立即发一条 READY 事件让前端确认连接建立
 *   4. 用共享 ScheduledExecutorService 调度心跳，每 30 秒发一条 HEARTBEAT（防 gateway response-timeout 660s）
 *
 * 客户端断开时 SseEmitter 会自动触发 completion 回调，registry 会移除该 emitter，
 * 同时取消对应的心跳 ScheduledFuture。
 *
 * 验证端点（Phase 3 第一步，保留）：
 *   GET /api/registration/calling/stream/ping
 *   立即发 PING 后每 5 秒发 HEARTBEAT，60 秒后关闭
 */
@Slf4j
@RestController
@RequestMapping("/api/registration/calling/stream")
@RequiredArgsConstructor
public class CallingStreamController {

    /** 心跳间隔：30 秒，必须短于 gateway response-timeout(660s) */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    /** 心跳任务最长跑的时长上限（24 小时），防止 emitter 永久占用调度槽 */
    private static final long MAX_LIFETIME_MS = 24 * 60 * 60 * 1000L;

    private final CallingSubscriberRegistry registry;

    /**
     * 所有 SSE 心跳共享一个 2 线程的调度池。
     * 即便同时挂 100 个大屏，也只用 2 个线程轮转跑心跳（30s 间隔，单次 send 耗时毫秒级，2 线程绰绰有余）。
     * 比"每个订阅一个守护线程"省线程也省内存。
     */
    private ScheduledExecutorService heartbeatScheduler;

    /** emitter → 对应的心跳 ScheduledFuture，断开时用于取消调度 */
    private final ConcurrentMap<SseEmitter, ScheduledFuture<?>> heartbeatFutures = new ConcurrentHashMap<>();

    @PostConstruct
    void initScheduler() {
        // 2 个线程：即使某个线程因单次 send 阻塞也不会让全部心跳停摆
        heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdownScheduler() {
        if (heartbeatScheduler == null) return;
        heartbeatScheduler.shutdownNow();
        try {
            heartbeatScheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 通用订阅：注册 emitter 到指定 topic + 调度心跳 */
    private SseEmitter subscribe(String topic) {
        SseEmitter emitter = new SseEmitter(0L);
        registry.subscribe(topic, emitter);

        // 立即发 READY，让前端确认连接已建立
        try {
            emitter.send(SseEmitter.event()
                    .name("READY")
                    .data("{\"topic\":\"" + topic + "\"}")
                    .id("ready"));
        } catch (IOException e) {
            log.error("[SSE] 发 READY 失败 topic={}: {}", topic, e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        // 调度周期心跳（initialDelay = HEARTBEAT_INTERVAL_MS，第一次心跳在 30s 后）
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(
                new HeartbeatTask(topic, emitter),
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        heartbeatFutures.put(emitter, future);

        // emitter 终止时取消心跳并清理映射
        Runnable cleanup = () -> {
            ScheduledFuture<?> f = heartbeatFutures.remove(emitter);
            if (f != null) f.cancel(false);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    /**
     * 单个 emitter 的心跳任务。scheduleAtFixedRate 抛 RuntimeException 会终止后续调度，
     * 所以内部捕获所有异常，失败时显式 completeWithError 并 cancel 自己。
     */
    private static class HeartbeatTask implements Runnable {
        private final String topic;
        private final SseEmitter emitter;
        private final long start = System.currentTimeMillis();
        private int seq = 1;

        HeartbeatTask(String topic, SseEmitter emitter) {
            this.topic = topic;
            this.emitter = emitter;
        }

        @Override
        public void run() {
            if (System.currentTimeMillis() - start >= MAX_LIFETIME_MS) {
                try { emitter.complete(); } catch (Exception ignore) {}
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("HEARTBEAT")
                        .data("tick " + seq)
                        .id("hb-" + seq));
                seq++;
            } catch (IllegalStateException ise) {
                // emitter 已 complete，正常情况；让调度器把它清掉
                emitter.complete();
            } catch (Exception e) {
                log.warn("[SSE] 心跳发送失败 topic={}: {}", topic, e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    // ==================== 正式订阅端点 ====================

    @GetMapping("/department/{departmentId}")
    public SseEmitter subscribeDepartment(@PathVariable Long departmentId) {
        return subscribe("department:" + departmentId);
    }

    @GetMapping("/doctor/{doctorId}")
    public SseEmitter subscribeDoctor(@PathVariable Long doctorId) {
        return subscribe("doctor:" + doctorId);
    }

    @GetMapping("/global")
    public SseEmitter subscribeGlobal() {
        return subscribe("global");
    }

    // ==================== Phase 3 验证端点（保留）====================

    /**
     * Phase 3 验证端点：连接后立即发 PING，每 5 秒发 HEARTBEAT，60 秒后关闭。
     * 不注册到 registry，单纯验证 SSE 全链路连通。
     */
    @GetMapping("/ping")
    public SseEmitter ping() {
        SseEmitter emitter = new SseEmitter(70_000L);
        log.info("[SSE] 客户端订阅 /calling/stream/ping");

        Thread t = new Thread(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("PING")
                        .data("hello from registration-service")
                        .id("0"));
                for (int i = 1; i <= 12; i++) {
                    Thread.sleep(5_000L);
                    emitter.send(SseEmitter.event()
                            .name("HEARTBEAT")
                            .data("tick " + i)
                            .id(String.valueOf(i)));
                }
                emitter.complete();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("[SSE] ping 异常: {}", e.getMessage());
            }
        }, "sse-ping-demo");
        t.setDaemon(true);
        t.start();

        emitter.onCompletion(() -> log.info("[SSE] ping 流结束"));
        emitter.onError(e -> log.error("[SSE] ping 异常: {}", e.getMessage()));
        return emitter;
    }
}
