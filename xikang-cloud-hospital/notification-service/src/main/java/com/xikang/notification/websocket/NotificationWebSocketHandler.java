package com.xikang.notification.websocket;

import com.xikang.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知 WebSocket 处理器
 * <p>连接 URL：ws://host/ws/notification?token=JWT&receiverId=xxx&receiverRole=patient
 * <p>客户端心跳：发 {"event":"ping"} 文本，服务端回 {"event":"pong"}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final UserSessionRegistry sessionRegistry;

    public static final String ATTR_RECEIVER_ID = "receiverId";
    public static final String ATTR_RECEIVER_ROLE = "receiverRole";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long receiverId = (Long) session.getAttributes().get(ATTR_RECEIVER_ID);
        String receiverRole = (String) session.getAttributes().get(ATTR_RECEIVER_ROLE);
        if (receiverId == null || receiverRole == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("missing auth"));
            } catch (Exception ignored) {
            }
            return;
        }
        sessionRegistry.register(receiverId, receiverRole, session);
        // 连接建立后立即推一条 hello，便于前端确认通道可用
        try {
            Map<String, Object> hello = new HashMap<>();
            hello.put("event", "hello");
            hello.put("receiverId", receiverId);
            hello.put("receiverRole", receiverRole);
            hello.put("serverTime", System.currentTimeMillis());
            session.sendMessage(new TextMessage(toJson(hello)));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 简单心跳协议：客户端发 {"event":"ping"}，服务端回 {"event":"pong"}
        if (payload != null && payload.contains("\"ping\"")) {
            try {
                session.sendMessage(new TextMessage("{\"event\":\"pong\",\"t\":" + System.currentTimeMillis() + "}"));
            } catch (Exception ignored) {
            }
            return;
        }
        // 其他客户端上行暂不处理（仅做 echo log）
        log.debug("WS 上行消息（暂不处理）：{}", payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        log.info("WS 连接关闭：sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输错误：sessionId={}, err={}", session.getId(), exception.getMessage());
        sessionRegistry.unregister(session);
    }

    /** 极简 JSON 拼接（避免引入 Jackson 依赖）。仅用于 hello/pong 等控制消息。 */
    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(v).append('"');
        }
        return sb.append('}').toString();
    }
}
