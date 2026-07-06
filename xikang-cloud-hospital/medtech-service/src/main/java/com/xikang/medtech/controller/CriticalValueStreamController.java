package com.xikang.medtech.controller;

import com.xikang.medtech.sse.CriticalValueSubscriberRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/medtech/critical-value/stream")
@RequiredArgsConstructor
public class CriticalValueStreamController {

    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final long MAX_LIFETIME_MS = 24 * 60 * 60 * 1000L;

    private final CriticalValueSubscriberRegistry registry;

    @GetMapping("/doctor/{doctorId}")
    public SseEmitter subscribeDoctor(@PathVariable Long doctorId) {
        return subscribe("critical:doctor:" + doctorId);
    }

    @GetMapping("/board")
    public SseEmitter subscribeBoard() {
        return subscribe("critical:board");
    }

    private SseEmitter subscribe(String topic) {
        SseEmitter emitter = new SseEmitter(0L);
        registry.subscribe(topic, emitter);
        try {
            emitter.send(SseEmitter.event()
                .name("READY")
                .data("{\"topic\":\"" + topic + "\"}")
                .id("ready"));
        } catch (IOException ex) {
            log.error("[CriticalSSE] READY failed topic={}: {}", topic, ex.getMessage());
            emitter.completeWithError(ex);
            return emitter;
        }
        Thread t = new Thread(() -> heartbeatLoop(topic, emitter), "critical-sse-" + topic);
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
                    emitter.send(SseEmitter.event().name("HEARTBEAT").data("tick " + seq).id("hb-" + seq));
                    seq++;
                } catch (IllegalStateException | IOException ex) {
                    return;
                }
            }
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
