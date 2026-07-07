package com.xikang.registration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled 线程池配置。
 *
 * 默认情况下 @EnableScheduling 只用单线程跑所有 @Scheduled 任务。
 * registration-service 现有两个调度任务：
 *   - CallingTimeoutScheduler（每分钟，叫号应答超时自动过号）
 *   - MissedAppointmentScheduler（每小时，未报到自动爽约）
 *
 * 单线程时若 CallingTimeoutScheduler 跑慢（例如积压大量超时记录逐条调 pass），
 * 会延迟 MissedAppointmentScheduler 的触发。设 poolSize=2 后两者互不影响。
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final int POOL_SIZE = 2;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("reg-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
