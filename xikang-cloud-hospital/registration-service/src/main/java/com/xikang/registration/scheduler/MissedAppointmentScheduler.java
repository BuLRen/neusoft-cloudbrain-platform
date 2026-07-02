package com.xikang.registration.scheduler;

import com.xikang.registration.entity.Register;
import com.xikang.registration.mapper.RegistrationMapper;
import com.xikang.registration.service.RegistrationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爽约扫描任务
 *
 * 业务规则：挂号记录过了"所属时段的截止时间"仍未报到的，置为「爽约」（visit_state = 7）。
 *   - 上午号 → 当天 12:00 截止
 *   - 下午号 → 当天 18:00 截止
 *   - 其他   → 当天 22:00 兜底
 * 截止时刻统一由 RegistrationService.computeMissDeadline 计算，保证多处判定口径一致。
 *
 * 与旧实现的关键差异：
 *   旧版用 visit_date < now() 判定，但 visit_date 实际存的是当天 00:00:00，
 *   导致当天 00:00 一过就会把上午号误判为爽约。
 *   新版按"时段截止时间"逐条判定，今天未到截止的不会被误伤。
 *
 * 触发：
 *   1) 服务每次启动时补扫一次（@PostConstruct），清理停机期间累积的脏数据
 *   2) 每小时第 5 分钟扫描一次，错开整点避免与其它定时任务撞车
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
        LocalDateTime now = LocalDateTime.now();
        try {
            // 查所有"已挂号但未报到"的候选，逐条按时段判定
            // 候选条件：visit_state = 1 且 check_in_time IS NULL
            // 注意：这里不再加 visit_date < now 的过滤，
            //       因为判定逻辑已挪到 Java 侧 computeMissDeadline
            List<Register> candidates = registrationMapper.selectMissedCandidates(now);
            if (candidates.isEmpty()) {
                return;
            }

            int affected = 0;
            for (Register r : candidates) {
                LocalDateTime deadline = RegistrationService.computeMissDeadline(
                        r.getVisitDate(), r.getNoon());
                if (now.isAfter(deadline)) {
                    int updated = registrationMapper.markMissedById(r.getId());
                    affected += updated;
                    if (updated > 0) {
                        log.info("标记爽约 | id={}, patient={}, visitDate={}, noon={}, deadline={}",
                                r.getId(), r.getRealName(), r.getVisitDate(), r.getNoon(), deadline);
                    }
                }
            }
            log.info("爽约{} | 候选 {} 条，标记 {} 条 | now={}",
                    tag, candidates.size(), affected, now);
        } catch (Exception e) {
            log.error("爽约{}失败 | now={}", tag, now, e);
        }
    }
}
