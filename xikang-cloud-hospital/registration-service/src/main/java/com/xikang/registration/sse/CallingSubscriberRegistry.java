package com.xikang.registration.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 订阅注册中心（设计文档 §5.4）。
 *
 * 维护按 topic 分组的 SseEmitter 集合。topic 命名：
 *   "department:{departmentId}"   科室叫号大屏
 *   "doctor:{doctorId}"            报到机/医生工作站
 *   "global"                       全院大屏
 *
 * 线程安全：emitters 用 ConcurrentHashMap.newKeySet()，并发增删不冲突。
 *
 * 设计决策：
 *   - 单实例方案（设计文档 §9.1 风险#2）：多实例下 A 实例叫号不会推到 B 实例的 emitter，
 *     要多实例需要 Redis Pub/Sub 转发，本阶段单实例不解决
 *   - 心跳由 CallingStreamController 在订阅时启动定时器维护
 */
@Slf4j
@Component
public class CallingSubscriberRegistry {

    private final Map<String, Set<SseEmitter>> topics = new ConcurrentHashMap<>();

    /** 注册一个 emitter 到指定 topic */
    public void subscribe(String topic, SseEmitter emitter) {
        topics.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(topic, emitter));
        emitter.onTimeout(() -> {
            log.warn("[SSE] emitter 超时，topic={}", topic);
            emitter.complete();
            remove(topic, emitter);
        });
        emitter.onError(e -> {
            log.warn("[SSE] emitter 异常，topic={}, err={}", topic, e.getMessage());
            remove(topic, emitter);
        });
        log.info("[SSE] 订阅 topic={}, 当前该 topic 在线={}", topic, topics.get(topic).size());
    }

    /** 移除指定 emitter */
    private void remove(String topic, SseEmitter emitter) {
        Set<SseEmitter> set = topics.get(topic);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                topics.remove(topic);
            }
        }
    }

    /** 获取指定 topic 的所有 emitter（只读视图，调用方负责处理 IOException） */
    public Set<SseEmitter> getEmitters(String topic) {
        Set<SseEmitter> set = topics.get(topic);
        return set != null ? Set.copyOf(set) : Set.of();
    }

    /** 在线总数（监控用） */
    public int totalSubscribers() {
        return topics.values().stream().mapToInt(Set::size).sum();
    }
}
