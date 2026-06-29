package com.xikang.ai.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates {@code Authorization: Bearer <INTERNAL_AI_TOKEN>} for Dify internal callbacks.
 */
@Component
public class InternalAiAuthInterceptor implements HandlerInterceptor {

    private final InternalAiProperties internalAiProperties;
    private final ObjectMapper objectMapper;

    public InternalAiAuthInterceptor(InternalAiProperties internalAiProperties, ObjectMapper objectMapper) {
        this.internalAiProperties = internalAiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!internalAiProperties.isEnabled()) {
            writeError(response, 503, "内部 AI 接口未配置，请设置 INTERNAL_AI_TOKEN");
            return false;
        }

        String token = extractBearerToken(request);
        if (token == null || !token.equals(internalAiProperties.getToken())) {
            writeError(response, 401, "无效的内部访问令牌");
            return false;
        }
        return true;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? 401 : code);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}
