package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知服务内部调用入参（Feign -> notification-service）
 */
@Data
public class NotificationSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long receiverId;
    private String receiverRole;   // patient / physician / admin
    private String type;           // doctor_change / leave_approved / leave_rejected / adjust_pending / adjust_confirmed
    private String title;
    private String content;
    private String bizType;
    private Long bizId;

    public NotificationSendRequest() {
    }

    public NotificationSendRequest(Long receiverId, String receiverRole, String type,
                                   String title, String content,
                                   String bizType, Long bizId) {
        this.receiverId = receiverId;
        this.receiverRole = receiverRole;
        this.type = type;
        this.title = title;
        this.content = content;
        this.bizType = bizType;
        this.bizId = bizId;
    }
}
