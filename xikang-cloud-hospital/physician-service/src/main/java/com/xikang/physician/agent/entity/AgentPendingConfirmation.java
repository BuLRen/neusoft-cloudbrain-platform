package com.xikang.physician.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentPendingConfirmation {
    private Long id;
    private String token;
    private Long registerId;
    private Long doctorId;
    private Long sessionId;
    private String actionType;
    private String payloadJson;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
}
