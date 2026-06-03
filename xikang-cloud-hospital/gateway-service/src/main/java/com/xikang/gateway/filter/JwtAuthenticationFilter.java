package com.xikang.gateway.filter;

import com.xikang.common.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter for API Gateway
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String AUTH_WHITELIST = "/api/auth/login,/api/auth/register,/api/auth/refresh,/api/auth/logout,/api/auth/me,/ws/voice,/api/voice/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for whitelist paths
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (token == null) {
            log.warn("No token found for path: {}", path);
            return unauthorized(exchange.getResponse());
        }

        if (!JwtUtils.validateToken(token)) {
            log.warn("Invalid token for path: {}", path);
            return unauthorized(exchange.getResponse());
        }

        // Add user info to headers
        String userId = JwtUtils.getSubject(token);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : AUTH_WHITELIST.split(",")) {
            if (path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String extractToken(ServerWebExchange exchange) {
        // Prefer HttpOnly cookie token (recommended)
        var cookie = exchange.getRequest().getCookies().getFirst(ACCESS_COOKIE_NAME);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        // Fallback to Authorization Bearer token (useful for debugging)
        return extractToken(exchange.getRequest());
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
