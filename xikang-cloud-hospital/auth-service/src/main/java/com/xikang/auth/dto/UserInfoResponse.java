package com.xikang.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * User info DTO for current user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private Long deptId;
    private String deptName;
    private Long registLevelId;
    private String registLevelName;
    private List<PatientInfo> patients;  // 患者列表（本人+家人）

    /**
     * 患者信息（简化版）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientInfo {
        private Integer patientId;
        private String realName;
        private String gender;
        private String relation;
        private Integer isPrimary;
        private BigDecimal accountBalance;
        private String allergyHistory;
    }
}