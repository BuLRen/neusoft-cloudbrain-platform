package com.xikang.registration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RegistrationWebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final ClinicalRecordAuthInterceptor clinicalRecordAuthInterceptor;

    public RegistrationWebMvcConfig(
        AdminAuthInterceptor adminAuthInterceptor,
        ClinicalRecordAuthInterceptor clinicalRecordAuthInterceptor
    ) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.clinicalRecordAuthInterceptor = clinicalRecordAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api/registration/admin/**");
        registry.addInterceptor(clinicalRecordAuthInterceptor)
            .addPathPatterns("/api/registration/clinical-record/**")
            .addPathPatterns("/api/registration/managed/**");
    }
}
