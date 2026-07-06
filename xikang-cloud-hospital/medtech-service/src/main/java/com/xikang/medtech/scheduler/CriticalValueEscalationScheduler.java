package com.xikang.medtech.scheduler;

import com.xikang.medtech.service.CriticalValueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalValueEscalationScheduler {

    private final CriticalValueService criticalValueService;

    @Scheduled(cron = "0 * * * * *")
    public void checkOverdue() {
        criticalValueService.escalateOverdue();
    }
}
