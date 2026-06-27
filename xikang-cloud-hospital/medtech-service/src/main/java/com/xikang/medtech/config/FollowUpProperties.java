package com.xikang.medtech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "follow-up")
public class FollowUpProperties {

    /** demo | production | hybrid */
    private String dataSource = "hybrid";

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource == null ? "hybrid" : dataSource.trim();
    }

    public boolean isDemoMode() {
        return "demo".equalsIgnoreCase(dataSource);
    }

    public boolean isProductionMode() {
        return "production".equalsIgnoreCase(dataSource);
    }

    public boolean isHybridMode() {
        return !isDemoMode() && !isProductionMode();
    }

    /** 非 demo 模式下工作台展示未纳入的可随访患者 */
    public boolean includeUnenrolledEligible() {
        return !isDemoMode();
    }

    /** production / hybrid 优先读 C 类 patient_health_observation */
    public boolean preferProductionObservations() {
        return isProductionMode() || isHybridMode();
    }

    /** production / hybrid 优先读 follow_up_enrollment */
    public boolean preferEnrollmentTable() {
        return isProductionMode() || isHybridMode();
    }
}
