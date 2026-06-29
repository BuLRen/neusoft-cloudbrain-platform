package com.xikang.physician.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PhysicianWebMvcConfig implements WebMvcConfigurer {

    private final PhysicianAuthInterceptor physicianAuthInterceptor;

    public PhysicianWebMvcConfig(PhysicianAuthInterceptor physicianAuthInterceptor) {
        this.physicianAuthInterceptor = physicianAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(physicianAuthInterceptor)
            .addPathPatterns("/api/physician/**")
            .excludePathPatterns("/actuator/**");
    }
}
