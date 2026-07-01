package com.xikang.physician.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.agent.AgentContextHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates internal service-to-service calls via {@link AgentContextHeaders#INTERNAL_TOKEN}.
 */
@Component
public class InternalServiceAuthInterceptor implements HandlerInterceptor {

    private final InternalAiProperties internalAiProperties;
    private final ObjectMapper objectMapper;

    public InternalServiceAuthInterceptor(InternalAiProperties internalAiProperties, ObjectMapper objectMapper) {
        this.internalAiProperties = internalAiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!internalAiProperties.isEnabled()) {
            writeError(response, 503, "内部接口未配置，请设置 INTERNAL_AI_TOKEN");
            return false;
        }
        String token = request.getHeader(AgentContextHeaders.INTERNAL_TOKEN);
        if (token == null || !token.equals(internalAiProperties.getToken())) {
            writeError(response, 401, "无效的内部访问令牌");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
