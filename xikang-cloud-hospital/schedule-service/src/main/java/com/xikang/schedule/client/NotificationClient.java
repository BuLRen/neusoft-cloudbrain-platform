package com.xikang.schedule.client;

import com.xikang.common.result.Result;
import com.xikang.schedule.dto.NotificationSendRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 通知服务 Feign 客户端
 * <p>schedule-service 调用 notification-service 写入消息。
 */
@FeignClient(name = "notification-service", url = "${notification.service.url:}")
public interface NotificationClient {

    /**
     * 单条发送（X-Internal-Token 鉴权）
     */
    @PostMapping("/api/notification/send")
    Result<Void> send(@RequestBody NotificationSendRequest req,
                      @RequestHeader("X-Internal-Token") String token);

    /**
     * 批量发送（同内容群发）
     */
    @PostMapping("/api/notification/batch-send")
    Result<Integer> batchSend(@RequestBody List<NotificationSendRequest> reqs,
                              @RequestHeader("X-Internal-Token") String token);
}
