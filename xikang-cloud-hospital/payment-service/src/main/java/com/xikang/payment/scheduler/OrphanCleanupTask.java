package com.xikang.payment.scheduler;

import com.xikang.payment.mapper.ExpenseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时清理 orphan expense 行（v3.2 §4.2）。
 *
 * 触发场景：createRegistration 的 @Transactional 内调 Feign createItem 成功落库，
 * 但事务最终回滚 → register 不存在 → expense_record 留下 status=0 孤儿。
 *
 * 清理规则：status=0 + register 不存在 + create_time 超过 10 分钟。
 * 10 分钟窗口足够覆盖任何合理的 createRegistration 事务延迟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanCleanupTask {

    private final ExpenseRecordMapper expenseRecordMapper;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        try {
            int affected = expenseRecordMapper.invalidateOrphans(cutoff);
            if (affected > 0) {
                log.info("orphan 清理完成 | 标记 status=3 行数={}, cutoff={}", affected, cutoff);
            }
        } catch (Exception e) {
            log.error("orphan 清理失败 | cutoff={}", cutoff, e);
        }
    }
}
