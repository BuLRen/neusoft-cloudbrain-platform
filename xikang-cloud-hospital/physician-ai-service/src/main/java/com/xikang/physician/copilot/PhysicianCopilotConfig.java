package com.xikang.physician.copilot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhysicianCopilotConfig {

    @Bean
    @Qualifier("physicianCopilotChatClient")
    public ChatClient physicianCopilotChatClient(ChatClient.Builder builder, PhysicianCopilotTools tools) {
        return builder.defaultTools(tools).build();
    }
}
