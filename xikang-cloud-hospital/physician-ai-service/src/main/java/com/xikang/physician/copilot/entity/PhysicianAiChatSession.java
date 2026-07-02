package com.xikang.physician.copilot.entity;

import java.time.LocalDateTime;

public class PhysicianAiChatSession {

    private Long id;
    private Long registerId;
    private Long doctorId;
    private String title;
    private String difyConversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRegisterId() {
        return registerId;
    }

    public void setRegisterId(Long registerId) {
        this.registerId = registerId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDifyConversationId() {
        return difyConversationId;
    }

    public void setDifyConversationId(String difyConversationId) {
        this.difyConversationId = difyConversationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
