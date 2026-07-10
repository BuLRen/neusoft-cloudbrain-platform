package com.xikang.physician.calling;

import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * registration-service 叫号内部接口的门面客户端。
 *
 * <p>physician-service 不直接读写 register 表的叫号字段，而是通过 Feign 调 registration-service 的 internal 接口。
 * 设计文档 §5.4：physician-service 通过 Feign 调 registration-service。
 *
 * <p>URL 配置：calling.service.url / REGISTRATION_SERVICE_URL
 * <ul>
 *   <li>本地开发：默认 http://localhost:8091（直连 registration-service）</li>
 *   <li>远程 Nacos：REGISTRATION_SERVICE_URL 留空 + NACOS_DISCOVERY_ENABLED=true</li>
 * </ul>
 */
@Slf4j
@Component
public class RegistrationCallingClient {

    private final RegistrationCallingFeignClient feignClient;

    public RegistrationCallingClient(RegistrationCallingFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    /** 叫下一个 */
    public Map<String, Object> callNext(Long employeeId) {
        return invoke("POST /internal/call-next",
                () -> feignClient.callNext(Map.of("employeeId", employeeId)));
    }

    /** 叫指定号 */
    public Map<String, Object> callSpecific(Long registerId, Long operatorEmployeeId) {
        Map<String, Object> body = operatorEmployeeId == null
                ? Map.of()
                : Map.of("employeeId", operatorEmployeeId);
        return invoke("POST /internal/call/" + registerId,
                () -> feignClient.callSpecific(registerId, body));
    }

    /** 患者应答 */
    public Map<String, Object> answer(Long registerId) {
        return invoke("POST /internal/answer/" + registerId,
                () -> feignClient.answer(registerId));
    }

    /** 标记过号 */
    public Map<String, Object> pass(Long registerId) {
        return invoke("POST /internal/pass/" + registerId,
                () -> feignClient.pass(registerId));
    }

    /** 查当前叫号 */
    public Map<String, Object> currentCalling(Long employeeId) {
        return invoke("GET /current?employeeId=" + employeeId,
                () -> feignClient.current(employeeId));
    }

    /** 医生候诊队列 */
    public Map<String, Object> doctorWaitingQueue(Long employeeId) {
        return invoke("GET /internal/queue/doctor?employeeId=" + employeeId,
                () -> feignClient.doctorWaitingQueue(employeeId));
    }

    /** 调整候诊队列 */
    public Map<String, Object> reorderQueue(Long employeeId, List<Long> registerIds) {
        return invoke("PUT /internal/queue/reorder",
                () -> feignClient.reorderQueue(Map.of("employeeId", employeeId, "registerIds", registerIds)));
    }

    private Map<String, Object> invoke(String action, FeignCall call) {
        try {
            return fromResult(call.execute());
        } catch (Exception e) {
            log.error("调 registration-service 失败 {}: {}", action, e.getMessage());
            return Map.of("code", 500, "message", "调叫号服务失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromResult(Result<?> result) {
        if (result == null) {
            return Map.of("code", 500, "message", "registration-service 返回空响应");
        }
        if (result.getCode() == Result.SUCCESS_CODE) {
            Object data = result.getData();
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            if (data == null) {
                return Map.of("code", 200);
            }
            return Map.of("code", 200, "data", data);
        }
        String message = result.getMessage() != null ? result.getMessage() : "";
        return Map.of("code", result.getCode(), "message", message);
    }

    @FunctionalInterface
    private interface FeignCall {
        Result<?> execute();
    }
}
