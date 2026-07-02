package com.xikang.registration.controller;

import com.xikang.registration.sse.CallingSubscriberRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

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
 *   4. 启动心跳线程，每 30 秒发一条 HEARTBEAT（防 gateway response-timeout 660s）
 *
 * 客户端断开时 SseEmitter 会自动触发 completion 回调，registry 会移除该 emitter。
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
    /** 心跳守护线程跑的时长上限（24 小时），防止 emitter 永久占用线程 */
    private static final long MAX_LIFETIME_MS = 24 * 60 * 60 * 1000L;

    private final CallingSubscriberRegistry registry;

    /** 通用订阅：注册 emitter 到指定 topic + 启动心跳 */
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

        // 启动心跳守护线程
        Thread t = new Thread(() -> heartbeatLoop(topic, emitter), "sse-heart-" + topic);
        t.setDaemon(true);
        t.start();

        return emitter;
    }

    private void heartbeatLoop(String topic, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        int seq = 1;
        try {
            while (System.currentTimeMillis() - start < MAX_LIFETIME_MS) {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                try {
                    emitter.send(SseEmitter.event()
                            .name("HEARTBEAT")
                            .data("tick " + seq)
                            .id("hb-" + seq));
                    seq++;
                } catch (IllegalStateException ise) {
                    // emitter 已 complete，正常退出
                    return;
                } catch (IOException ioe) {
                    log.warn("[SSE] 心跳发送失败 topic={}: {}", topic, ioe.getMessage());
                    emitter.completeWithError(ioe);
                    return;
                }
            }
            // 到达最大寿命，正常关闭
            log.info("[SSE] 到达最大寿命 {} ms，关闭 topic={}", MAX_LIFETIME_MS, topic);
            emitter.complete();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
