package com.xikang.physician.config;

import com.xikang.common.agent.AgentContextHeaders;
import com.xikang.common.agent.AgentToolExecutionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rebuilds {@link AgentToolExecutionContext} from Feign-propagated headers on internal API calls.
 */
@Component
public class InternalAgentContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long doctorId = parseLong(request.getHeader(AgentContextHeaders.DOCTOR_ID));
        Long sessionId = parseLong(request.getHeader(AgentContextHeaders.SESSION_ID));
        String requestId = request.getHeader(AgentContextHeaders.REQUEST_ID);
        String toolName = request.getHeader(AgentContextHeaders.TOOL_NAME);
        String riskRaw = request.getHeader(AgentContextHeaders.RISK_LEVEL);
        AgentToolExecutionContext.RiskLevel riskLevel = parseRisk(riskRaw);
        if (doctorId != null || sessionId != null || requestId != null || toolName != null || riskLevel != null) {
            AgentToolExecutionContext.enable(new AgentToolExecutionContext.Context(
                doctorId, sessionId, requestId, toolName, riskLevel != null ? riskLevel : AgentToolExecutionContext.RiskLevel.READ
            ));
        } else {
            AgentToolExecutionContext.enable();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AgentToolExecutionContext.clear();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private AgentToolExecutionContext.RiskLevel parseRisk(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentToolExecutionContext.RiskLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
