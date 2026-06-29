package com.xikang.ai.catalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CatalogWebMvcConfig implements WebMvcConfigurer {

    private final InternalAiAuthInterceptor internalAiAuthInterceptor;

    public CatalogWebMvcConfig(InternalAiAuthInterceptor internalAiAuthInterceptor) {
        this.internalAiAuthInterceptor = internalAiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAiAuthInterceptor)
            .addPathPatterns("/api/physician/internal/**");
    }
}
