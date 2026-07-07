package com.xikang.notification.websocket;

import com.xikang.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器
 * <p>从 query 解析 token / receiverId / receiverRole：
 * <ul>
 *   <li>token 必填，必须是有效的 JWT（与 gateway 共用 secret）</li>
 *   <li>receiverId + receiverRole 必填，且必须与 token claims 中的身份匹配</li>
 * </ul>
 * 鉴权通过后将 receiverId/role 注入 session attributes，供 Handler 后续使用。
 */
@Slf4j
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String query = uri.getQuery() == null ? "" : uri.getQuery();
        Map<String, String> params = parseQuery(query);

        String token = params.get("token");
        String receiverIdStr = params.get("receiverId");
        String receiverRole = params.get("receiverRole");

        if (token == null || token.isBlank() || receiverIdStr == null || receiverRole == null) {
            log.warn("WS 握手拒绝：参数缺失，uri={}", uri);
            return false;
        }

        Claims claims = JwtUtils.parseToken(token);
        if (claims == null) {
            log.warn("WS 握手拒绝：JWT 解析失败");
            return false;
        }
        // 校验 role 与 token 内 claim 是否一致
        Object tokenRole = claims.get("role");
        if (tokenRole != null && !tokenRole.toString().equalsIgnoreCase(receiverRole)) {
            log.warn("WS 握手拒绝：role 与 token 不一致，tokenRole={}, paramRole={}", tokenRole, receiverRole);
            return false;
        }

        try {
            Long receiverId = Long.parseLong(receiverIdStr);
            attributes.put(NotificationWebSocketHandler.ATTR_RECEIVER_ID, receiverId);
            attributes.put(NotificationWebSocketHandler.ATTR_RECEIVER_ROLE, receiverRole);
            return true;
        } catch (NumberFormatException e) {
            log.warn("WS 握手拒绝：receiverId 不是数字，{}", receiverIdStr);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = pair.substring(0, idx);
                String v = pair.substring(idx + 1);
                try {
                    map.put(k, java.net.URLDecoder.decode(v, "UTF-8"));
                } catch (Exception e) {
                    map.put(k, v);
                }
            }
        }
        return map;
    }
}
