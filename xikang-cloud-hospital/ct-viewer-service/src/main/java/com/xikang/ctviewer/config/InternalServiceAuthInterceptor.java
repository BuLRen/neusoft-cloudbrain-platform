package com.xikang.ctviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.agent.AgentContextHeaders;
import com.xikang.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalServiceAuthInterceptor implements HandlerInterceptor {

    private final InternalServiceProperties internalServiceProperties;
    private final ObjectMapper objectMapper;

    public InternalServiceAuthInterceptor(
        InternalServiceProperties internalServiceProperties,
        ObjectMapper objectMapper
    ) {
        this.internalServiceProperties = internalServiceProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!internalServiceProperties.isEnabled()) {
            writeError(response, 503, "内部接口未配置，请设置 INTERNAL_SERVICE_TOKEN");
            return false;
        }
        String token = request.getHeader(AgentContextHeaders.INTERNAL_TOKEN);
        if (token == null || !token.equals(internalServiceProperties.getToken())) {
            writeError(response, 401, "无效的内部访问令牌");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}
