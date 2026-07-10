package com.xikang.physician.calling;

import com.xikang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * registration-service 叫号内部接口 Feign 客户端。
 * <p>url 留空时走 Nacos 服务发现；本地开发可设 calling.service.url=http://localhost:8091 直连。
 */
@FeignClient(name = "registration-service", url = "${calling.service.url:}")
public interface RegistrationCallingFeignClient {

    @PostMapping("/api/registration/calling/internal/call-next")
    Result<Map<String, Object>> callNext(@RequestBody Map<String, Object> body);

    @PostMapping("/api/registration/calling/internal/call/{registerId}")
    Result<Map<String, Object>> callSpecific(@PathVariable("registerId") Long registerId,
                                             @RequestBody(required = false) Map<String, Object> body);

    @PostMapping("/api/registration/calling/internal/answer/{registerId}")
    Result<Map<String, Object>> answer(@PathVariable("registerId") Long registerId);

    @PostMapping("/api/registration/calling/internal/pass/{registerId}")
    Result<Map<String, Object>> pass(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/registration/calling/current")
    Result<Map<String, Object>> current(@RequestParam("employeeId") Long employeeId);

    @GetMapping("/api/registration/calling/internal/queue/doctor")
    Result<List<Map<String, Object>>> doctorWaitingQueue(@RequestParam("employeeId") Long employeeId);

    @PutMapping("/api/registration/calling/internal/queue/reorder")
    Result<Void> reorderQueue(@RequestBody Map<String, Object> body);
}
