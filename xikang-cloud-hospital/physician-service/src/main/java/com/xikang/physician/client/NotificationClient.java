package com.xikang.physician.client;

import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * physician-service 调 notification-service 内部 API（写消息通知）。
 * <p>失败只 try-catch + log，绝不影响开单主流程。
 */
@Slf4j
@Service
public class NotificationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public NotificationClient(
            @Qualifier("notificationRestTemplate") RestTemplate restTemplate,
            @Value("${services.notification-service.url:http://localhost:8200}") String baseUrl,
            @Value("${services.notification-service.internal-token:notif-internal-2026}") String internalToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 单条发送（X-Internal-Token 鉴权）。
     * <p>调用失败仅 log，不抛异常 —— 通知是辅助，不能阻塞主业务。
     *
     * @param receiverId   接收者 ID（患者 patientId / 医生 employeeId）
     * @param receiverRole 接收者角色（patient / physician / admin）
     * @param type         消息类型（如 EXAM_FEE_CREATED）
     * @param title        标题
     * @param content      正文
     * @param bizType      关联业务类型（如 register）
     * @param bizId        关联业务 ID
     */
    public void trySend(Long receiverId, String receiverRole, String type,
                        String title, String content, String bizType, Long bizId) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverId", receiverId);
        body.put("receiverRole", receiverRole);
        body.put("type", type);
        body.put("title", title);
        body.put("content", content);
        body.put("bizType", bizType);
        body.put("bizId", bizId);

        try {
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body,
                            new org.springframework.http.HttpHeaders() {{
                                set("X-Internal-Token", internalToken);
                                set("Content-Type", "application/json");
                            }});
            Map<String, Object> resp = restTemplate.postForObject(
                    baseUrl + "/api/notification/send", entity, Map.class);
            Object code = resp != null ? resp.get("code") : null;
            if (code instanceof Number && ((Number) code).intValue() == Result.SUCCESS_CODE) {
                log.info("通知已发送 | receiverId={}, type={}, bizId={}", receiverId, type, bizId);
            } else {
                log.warn("通知发送返回非 200 | receiverId={}, resp={}", receiverId, resp);
            }
        } catch (RestClientException e) {
            log.warn("通知发送失败（不影响主流程） | receiverId={}, type={}, err={}",
                    receiverId, type, e.getMessage());
        } catch (Exception e) {
            // 兜底，绝不让通知拖垮开单
            log.error("通知发送出现未预期异常 | receiverId={}, type={}", receiverId, type, e);
        }
    }
}
