package com.xikang.medtech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MedtechWebMvcConfig implements WebMvcConfigurer {

    private final MedtechAuthInterceptor medtechAuthInterceptor;
    private final FollowUpStaffAuthInterceptor followUpStaffAuthInterceptor;
    private final PatientFollowUpAuthInterceptor patientFollowUpAuthInterceptor;

    public MedtechWebMvcConfig(
        MedtechAuthInterceptor medtechAuthInterceptor,
        FollowUpStaffAuthInterceptor followUpStaffAuthInterceptor,
        PatientFollowUpAuthInterceptor patientFollowUpAuthInterceptor
    ) {
        this.medtechAuthInterceptor = medtechAuthInterceptor;
        this.followUpStaffAuthInterceptor = followUpStaffAuthInterceptor;
        this.patientFollowUpAuthInterceptor = patientFollowUpAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(patientFollowUpAuthInterceptor)
            .addPathPatterns(
                "/api/medtech/follow-up/patient/**",
                "/api/medtech/follow-up/communication/sessions/*/patient-messages",
                "/api/medtech/follow-up/communication/patient/**"
            );

        registry.addInterceptor(followUpStaffAuthInterceptor)
            .addPathPatterns(
                "/api/medtech/follow-up/dashboard/**",
                "/api/medtech/follow-up/outcome/**",
                "/api/medtech/follow-up/communication/**",
                "/api/medtech/follow-up/history/**"
            )
            .excludePathPatterns(
                "/api/medtech/follow-up/communication/sessions/*/patient-messages",
                "/api/medtech/follow-up/communication/patient/**"
            );

        registry.addInterceptor(medtechAuthInterceptor)
            .addPathPatterns("/api/medtech/**")
            .excludePathPatterns(
                "/actuator/**",
                "/api/medtech/follow-up/patient/**",
                "/api/medtech/follow-up/dashboard/**",
                "/api/medtech/follow-up/outcome/**",
                "/api/medtech/follow-up/communication/**",
                "/api/medtech/follow-up/history/**"
            );
    }
}
