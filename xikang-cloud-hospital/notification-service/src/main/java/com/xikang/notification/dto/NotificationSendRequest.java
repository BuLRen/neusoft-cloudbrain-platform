package com.xikang.notification.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 内部 send API 入参（其他业务服务调用）
 */
@Data
public class NotificationSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long receiverId;       // 必填
    private String receiverRole;   // patient / physician / admin
    private String type;           // doctor_change / leave_approved / leave_rejected / adjust_pending / adjust_confirmed
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
}
