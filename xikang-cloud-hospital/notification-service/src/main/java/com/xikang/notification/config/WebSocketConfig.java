package com.xikang.notification.config;

import com.xikang.notification.websocket.NotificationWebSocketHandler;
import com.xikang.notification.websocket.WsAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 入口配置
 * <p>对外端点：/ws/notification
 * <p>允许所有来源（鉴权依赖 JWT，不依赖 Origin）
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
