package com.xikang.registration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class ClinicalRecordAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public ClinicalRecordAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            writeError(response, 401, "未授权，请先登录");
            return false;
        }
        Map<String, Object> claims = JwtUtils.parseToken(token);
        if (claims == null) {
            writeError(response, 401, "无效的令牌");
            return false;
        }
        String role = claims.get("role") != null ? String.valueOf(claims.get("role")) : "";
        if (!"patient".equals(role) && !"admin".equals(role)) {
            writeError(response, 403, "无权访问患者病历接口");
            return false;
        }
        Long userId = parseLong(claims.get("userId"));
        if (userId == null) {
            writeError(response, 401, "无效的令牌");
            return false;
        }
        request.setAttribute("clinicalRecordUserId", userId);
        request.setAttribute("clinicalRecordRole", role);
        request.setAttribute("clinicalRecordAdmin", "admin".equals(role));
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? 401 : 403);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}
