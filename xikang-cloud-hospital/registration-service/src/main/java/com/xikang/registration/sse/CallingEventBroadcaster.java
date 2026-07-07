package com.xikang.registration.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.entity.Register;
import com.xikang.registration.mapper.RegistrationMapper;
import com.xikang.registration.service.DepartmentService;
import com.xikang.registration.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 叫号事件广播器（设计文档 §5）。
 *
 * 业务侧（CallingService）在叫号/应答/过号后调用本组件，向所有订阅相关 topic 的客户端推送 SSE 事件。
 *
 * 推送范围：
 *   - 科室大屏：  topic = "department:{departmentId}"
 *   - 医生工作站：topic = "doctor:{doctorId}"
 *   - 全院大屏：  topic = "global"
 *   一条叫号事件通常会同时推给"科室大屏"+"医生个人"+"全院大屏"三类订阅者。
 *
 * 事件格式（SSE event）：
 *   event: CALLED | ANSWERED | PASSED
 *   data:  { ... payload ... }
 *
 * 推送失败处理：单个 emitter 写失败不影响其他，失败后从 registry 移除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallingEventBroadcaster {

    private final CallingSubscriberRegistry registry;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final RegistrationMapper registrationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void broadcastCalled(Register reg) {
        broadcast("CALLED", reg);
    }

    public void broadcastAnswered(Register reg) {
        broadcast("ANSWERED", reg);
    }

    public void broadcastPassed(Register reg) {
        broadcast("PASSED", reg);
    }

    /**
     * 把事件推给"科室 + 医生个人 + 全院"三类订阅者。
     * payload 平铺（无 data 嵌套层），前端 JSON.parse(e.data) 后可直接读 payload.registerId 等字段。
     */
    private void broadcast(String eventType, Register reg) {
        Map<String, Object> payload = buildPayload(eventType, reg);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[SSE] 序列化失败，事件被丢弃：{}", e.getMessage());
            return;
        }

        // 1. 科室大屏
        if (reg.getDeptmentId() != null) {
            sendToTopic("department:" + reg.getDeptmentId(), eventType, json);
        }
        // 2. 医生个人（报到机/工作站用）
        if (reg.getEmployeeId() != null) {
            sendToTopic("doctor:" + reg.getEmployeeId(), eventType, json);
        }
        // 3. 全院大屏（可选订阅）
        sendToTopic("global", eventType, json);
    }

    private void sendToTopic(String topic, String eventType, String json) {
        Set<SseEmitter> emitters = registry.getEmitters(topic);
        if (emitters.isEmpty()) return;
        int success = 0, fail = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(json));
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[SSE] 推送失败，移除该 emitter：topic={}, err={}", topic, e.getMessage());
                emitter.completeWithError(e);
            }
        }
        log.info("[SSE] 推送 topic={} event={} success={} fail={}", topic, eventType, success, fail);
    }

    /**
     * 组装事件 payload（平铺格式，无 data 嵌套）。
     * 字段：type / registerId / patientName / caseNumber / callStatus / callRound /
     *      calledTime / answeredTime / departmentId / departmentName /
     *      doctorId / doctorName / queueNumber
     */
    private Map<String, Object> buildPayload(String type, Register reg) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("registerId", reg.getId());
        payload.put("patientName", reg.getRealName());
        payload.put("caseNumber", reg.getCaseNumber());
        payload.put("callStatus", reg.getCallStatus());
        payload.put("callRound", reg.getCallRound());
        payload.put("calledTime", reg.getCalledTime());
        payload.put("answeredTime", reg.getAnsweredTime());
        payload.put("departmentId", reg.getDeptmentId());
        payload.put("doctorId", reg.getEmployeeId());

        if (reg.getDeptmentId() != null) {
            Department dept = departmentService.getDepartment(reg.getDeptmentId());
            if (dept != null) payload.put("departmentName", dept.getName());
        }
        if (reg.getEmployeeId() != null) {
            Employee doc = employeeService.getDoctor(reg.getEmployeeId());
            if (doc != null) {
                payload.put("doctorName", doc.getRealname());
                payload.put("clinicRoom", doc.getClinicRoom());
            }
        }

        // 号序（基于 check_in_time）
        if (reg.getId() != null) {
            int before = registrationMapper.countWaitingBefore(reg.getId());
            payload.put("queueNumber", before + 1);
        }
        return payload;
    }
}
