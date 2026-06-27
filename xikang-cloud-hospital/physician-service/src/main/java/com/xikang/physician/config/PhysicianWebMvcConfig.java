package com.xikang.physician.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PhysicianWebMvcConfig implements WebMvcConfigurer {

    private final PhysicianAuthInterceptor physicianAuthInterceptor;
    private final InternalAiAuthInterceptor internalAiAuthInterceptor;

    public PhysicianWebMvcConfig(
        PhysicianAuthInterceptor physicianAuthInterceptor,
        InternalAiAuthInterceptor internalAiAuthInterceptor
    ) {
        this.physicianAuthInterceptor = physicianAuthInterceptor;
        this.internalAiAuthInterceptor = internalAiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAiAuthInterceptor)
            .addPathPatterns("/api/physician/internal/**");

        registry.addInterceptor(physicianAuthInterceptor)
            .addPathPatterns("/api/physician/**")
            .excludePathPatterns("/actuator/**", "/api/physician/internal/**");
    }
}
