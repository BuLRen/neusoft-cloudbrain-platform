package com.xikang.physician.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PhysicianWebMvcConfig implements WebMvcConfigurer {

    private final PhysicianAuthInterceptor physicianAuthInterceptor;
    private final InternalServiceAuthInterceptor internalServiceAuthInterceptor;
    private final InternalAgentContextInterceptor internalAgentContextInterceptor;

    public PhysicianWebMvcConfig(
        PhysicianAuthInterceptor physicianAuthInterceptor,
        InternalServiceAuthInterceptor internalServiceAuthInterceptor,
        InternalAgentContextInterceptor internalAgentContextInterceptor
    ) {
        this.physicianAuthInterceptor = physicianAuthInterceptor;
        this.internalServiceAuthInterceptor = internalServiceAuthInterceptor;
        this.internalAgentContextInterceptor = internalAgentContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalServiceAuthInterceptor)
            .addPathPatterns("/api/physician/internal/**");
        registry.addInterceptor(internalAgentContextInterceptor)
            .addPathPatterns("/api/physician/internal/**");

        registry.addInterceptor(physicianAuthInterceptor)
            .addPathPatterns("/api/physician/**")
            .excludePathPatterns("/actuator/**", "/api/physician/internal/**");
    }
}
