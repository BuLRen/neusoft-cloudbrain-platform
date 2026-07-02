package com.xikang.ctviewer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CtViewerWebMvcConfig implements WebMvcConfigurer {

    private final CtViewerAuthInterceptor ctViewerAuthInterceptor;
    private final InternalServiceAuthInterceptor internalServiceAuthInterceptor;

    public CtViewerWebMvcConfig(
        CtViewerAuthInterceptor ctViewerAuthInterceptor,
        InternalServiceAuthInterceptor internalServiceAuthInterceptor
    ) {
        this.ctViewerAuthInterceptor = ctViewerAuthInterceptor;
        this.internalServiceAuthInterceptor = internalServiceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalServiceAuthInterceptor)
            .addPathPatterns("/api/ct-viewer/internal/**");

        registry.addInterceptor(ctViewerAuthInterceptor)
            .addPathPatterns("/api/ct-viewer/**")
            .excludePathPatterns(
                "/api/ct-viewer/health",
                "/api/ct-viewer/internal/**"
            );
    }
}
