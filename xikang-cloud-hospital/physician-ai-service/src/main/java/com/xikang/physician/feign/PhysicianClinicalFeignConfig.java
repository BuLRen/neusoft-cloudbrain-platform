package com.xikang.physician.feign;

import com.xikang.common.agent.AgentContextHeaders;
import com.xikang.common.agent.AgentToolExecutionContext;
import com.xikang.physician.agent.InternalAiProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhysicianClinicalFeignConfig {

    @Bean
    public RequestInterceptor agentContextFeignInterceptor(InternalAiProperties internalAiProperties) {
        return template -> {
            template.header(AgentContextHeaders.INTERNAL_TOKEN, internalAiProperties.getToken());
            if (AgentToolExecutionContext.isActive()) {
                AgentToolExecutionContext.Context ctx = AgentToolExecutionContext.get();
                if (ctx != null) {
                    if (ctx.doctorId() != null) {
                        template.header(AgentContextHeaders.DOCTOR_ID, String.valueOf(ctx.doctorId()));
                    }
                    if (ctx.sessionId() != null) {
                        template.header(AgentContextHeaders.SESSION_ID, String.valueOf(ctx.sessionId()));
                    }
                    if (ctx.requestId() != null) {
                        template.header(AgentContextHeaders.REQUEST_ID, ctx.requestId());
                    }
                    if (ctx.toolName() != null) {
                        template.header(AgentContextHeaders.TOOL_NAME, ctx.toolName());
                    }
                    if (ctx.riskLevel() != null) {
                        template.header(AgentContextHeaders.RISK_LEVEL, ctx.riskLevel().name());
                    }
                }
            }
        };
    }
}
