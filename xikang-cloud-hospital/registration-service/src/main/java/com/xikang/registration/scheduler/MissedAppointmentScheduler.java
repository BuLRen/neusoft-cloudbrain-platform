package com.xikang.registration.scheduler;

import com.xikang.registration.mapper.RegistrationMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import com.xikang.registration.entity.Register;

/**
 * 爽约扫描任务
 *
 * 业务规则：挂号后过了就诊时间仍未接诊的，置为「爽约」（visit_state = 5）。
 * - 不退款、不释放号源（时段已过）
 * - 爽约后状态锁定，患者必须重新挂号
 *
 * 触发：
 * 1) 服务每次启动时补扫一次（@PostConstruct），清理掉停机期间遗留的脏数据
 * 2) 每小时整点附近扫描一次（@Scheduled），0 5 * * * * —— 错开整点避免与其它定时任务撞车
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissedAppointmentScheduler {

    private final RegistrationMapper registrationMapper;

    /**
     * 启动时补扫：把上次服务停机期间累积的爽约记录清理掉。
     */
    @PostConstruct
    public void sweepOnStartup() {
        sweep("启动补扫");
    }

    /**
     * 每小时第 5 分钟扫描一次。
     * cron = "0 5 * * * *"（秒 分 时 日 月 周）
     */
    @Scheduled(cron = "0 5 * * * *")
    public void sweepHourly() {
        sweep("定时扫描");
    }

    private void sweep(String tag) {
        LocalDateTime deadline = LocalDateTime.now();
        try {
            List<Register> candidates = registrationMapper.selectMissedCandidates(deadline);
            if (candidates.isEmpty()) {
                return;
            }
            int affected = registrationMapper.markMissed(deadline);
            log.info("爽约{} | 扫描到 {} 条，标记 {} 条 | deadline={}",
                    tag, candidates.size(), affected, deadline);
        } catch (Exception e) {
            log.error("爽约{}失败 | deadline={}", tag, deadline, e);
        }
    }
}
