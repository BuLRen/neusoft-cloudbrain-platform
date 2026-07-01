package com.xikang.physician.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentToolAuditLog {
    private Long id;
    private Long registerId;
    private Long doctorId;
    private Long sessionId;
    private String requestId;
    private String toolName;
    private String riskLevel;
    private String requestPayload;
    private String responsePayload;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String confirmSource;
    private String confirmationToken;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
