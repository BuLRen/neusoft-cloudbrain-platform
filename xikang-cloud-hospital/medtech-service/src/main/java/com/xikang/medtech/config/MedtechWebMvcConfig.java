package com.xikang.medtech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MedtechWebMvcConfig implements WebMvcConfigurer {

    private final MedtechAuthInterceptor medtechAuthInterceptor;

    public MedtechWebMvcConfig(MedtechAuthInterceptor medtechAuthInterceptor) {
        this.medtechAuthInterceptor = medtechAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(medtechAuthInterceptor)
            .addPathPatterns("/api/medtech/**")
            .excludePathPatterns("/actuator/**");
    }
}
