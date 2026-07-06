package com.xikang.medtech.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CriticalValueSubscriberRegistry {

    private final Map<String, Set<SseEmitter>> topics = new ConcurrentHashMap<>();

    public void subscribe(String topic, SseEmitter emitter) {
        topics.computeIfAbsent(topic, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(topic, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(topic, emitter);
        });
        emitter.onError(e -> remove(topic, emitter));
        log.info("[CriticalSSE] subscribe topic={}, count={}", topic, topics.get(topic).size());
    }

    private void remove(String topic, SseEmitter emitter) {
        Set<SseEmitter> set = topics.get(topic);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                topics.remove(topic);
            }
        }
    }

    public Set<SseEmitter> getEmitters(String topic) {
        Set<SseEmitter> set = topics.get(topic);
        return set != null ? Set.copyOf(set) : Set.of();
    }
}
