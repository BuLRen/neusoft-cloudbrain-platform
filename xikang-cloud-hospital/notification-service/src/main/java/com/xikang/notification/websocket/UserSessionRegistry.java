package com.xikang.notification.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户 WebSocket 会话注册中心
 * <p>按 receiverId + receiverRole 聚合所有在线连接（同一用户可能在多个端同时在线）。
 * 推送时遍历该 key 下所有 session 发送，发送失败的 session 静默忽略。
 */
@Slf4j
@Component
public class UserSessionRegistry {

    /** key = receiverId + ":" + receiverRole */
    private final ConcurrentHashMap<String, java.util.List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long receiverId, String receiverRole, WebSocketSession session) {
        if (receiverId == null || receiverRole == null) {
            return;
        }
        String key = key(receiverId, receiverRole);
        sessions.computeIfAbsent(key, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(session);
        log.info("WS 会话注册：key={}, sessionId={}, 当前在线数={}", key, session.getId(), countSessions(key));
    }

    public void unregister(WebSocketSession session) {
        sessions.forEach((key, list) -> {
            list.removeIf(s -> s.getId().equals(session.getId()));
            if (list.isEmpty()) {
                sessions.remove(key);
            }
        });
    }

    /** 推送给指定用户的所有在线连接，返回成功推送的连接数。 */
    public int sendToUser(Long receiverId, String receiverRole, String payload) {
        if (receiverId == null || receiverRole == null) {
            return 0;
        }
        String key = key(receiverId, receiverRole);
        java.util.List<WebSocketSession> list = sessions.get(key);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        TextMessage message = new TextMessage(payload);
        int ok = 0;
        // 复制一份避免并发修改
        WebSocketSession[] snapshot = list.toArray(new WebSocketSession[0]);
        for (WebSocketSession s : snapshot) {
            if (!s.isOpen()) {
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(message);
                }
                ok++;
            } catch (IOException e) {
                log.warn("WS 推送失败：key={}, sessionId={}, err={}", key, s.getId(), e.getMessage());
            }
        }
        return ok;
    }

    public int countSessions(String key) {
        java.util.List<WebSocketSession> list = sessions.get(key);
        return list == null ? 0 : list.size();
    }

    private String key(Long receiverId, String receiverRole) {
        return receiverId + ":" + receiverRole;
    }
}
