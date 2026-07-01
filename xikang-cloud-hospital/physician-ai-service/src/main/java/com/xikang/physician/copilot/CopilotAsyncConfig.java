package com.xikang.physician.copilot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class CopilotAsyncConfig {

    @Bean(name = "copilotChatExecutor")
    public Executor copilotChatExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "copilot-chat-" + seq.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }
}
