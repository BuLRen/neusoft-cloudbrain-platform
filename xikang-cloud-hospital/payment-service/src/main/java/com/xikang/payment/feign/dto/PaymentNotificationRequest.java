package com.xikang.payment.feign.dto;

import lombok.Data;

/**
 * 通知服务请求 DTO（与 notification-service 的 NotificationSendRequest 字段对齐）
 */
@Data
public class PaymentNotificationRequest {
    private Long receiverId;
    private String receiverRole;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
}
