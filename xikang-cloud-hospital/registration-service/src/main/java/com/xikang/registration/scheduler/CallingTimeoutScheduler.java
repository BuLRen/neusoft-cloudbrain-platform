package com.xikang.registration.scheduler;

import com.xikang.registration.entity.Register;
import com.xikang.registration.mapper.RegistrationMapper;
import com.xikang.registration.sse.CallingEventBroadcaster;
import com.xikang.registration.service.CallingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 叫号应答超时定时任务（设计文档 §9.2）。
 *
 * 规则：医生叫号后 5 分钟内患者未应答（未进诊室）→ 自动置为过号。
 *
 * 实现：
 *   - 每分钟扫一次 call_status=1 且 called_time 早于 (now - 5min) 的记录
 *   - 对每条记录调 CallingService.pass() 完成过号（含广播 PASSED 事件）
 *
 * 注意：与 MissedAppointmentScheduler 不同，本任务用 @Transactional 的 CallingService.pass 入口，
 * 保证事务边界一致；不直接调 mapper.markPassed 是为了让 broadcaster 也能推 PASSED 事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallingTimeoutScheduler {

    /** 应答超时：5 分钟 */
    private static final int ANSWER_TIMEOUT_MINUTES = 5;

    private final RegistrationMapper registrationMapper;
    private final CallingService callingService;

    /**
     * 每分钟扫一次。cron: "0 * * * * *" = 每分钟第 0 秒。
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(ANSWER_TIMEOUT_MINUTES);
        List<Register> candidates = registrationMapper.selectTimeoutCandidates(deadline);
        if (candidates.isEmpty()) return;

        log.info("[CALL-TIMEOUT] 发现 {} 条应答超时记录，开始自动过号", candidates.size());
        int success = 0, fail = 0;
        for (Register reg : candidates) {
            try {
                callingService.pass(reg.getId());
                success++;
                log.info("[CALL-TIMEOUT] 自动过号 registerId={}, patientName={}, calledTime={}",
                        reg.getId(), reg.getRealName(), reg.getCalledTime());
            } catch (Exception e) {
                fail++;
                log.warn("[CALL-TIMEOUT] 过号失败 registerId={}: {}", reg.getId(), e.getMessage());
            }
        }
        log.info("[CALL-TIMEOUT] 完成：success={}, fail={}", success, fail);
    }
}
