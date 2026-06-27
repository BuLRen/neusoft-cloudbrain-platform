package com.xikang.ai.triage.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 配置类 —— 按是否启用 RAG 装配不同的 ChatClient。
 *
 * <p>默认（{@code spring.ai.rag.enabled=false} 或未设置）：
 * 使用 Spring AI starter 自动配置的 DeepSeek ChatModel，不挂 RAG Advisor。
 *
 * <p>RAG 启用（{@code spring.ai.rag.enabled=true}）：
 * 通过 {@link Qualifier} 注入 {@link DashScopeChatModel}（同包 @Component @Primary），
 * 挂上 {@link QuestionAnswerAdvisor}，自动检索医院知识库。
 *
 * <p>两条装配路径互斥（@ConditionalOnProperty 控制），保证同一时刻只有一个 ChatClient Bean。
 */
@Configuration
public class AiConfig {

    /**
     * 默认 ChatClient：DeepSeek，不启用 RAG。
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "false", matchIfMissing = true)
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是熙康云医院的专业医疗分诊AI助手，根据患者症状给出科室、紧迫度、红旗征等结构化判断。")
                .build();
    }

    /**
     * RAG 启用时的 ChatClient：通义千问（DashScopeChatModel）+ QuestionAnswerAdvisor。
     *
     * <p>注入的 dashscopeChatModel 是 {@link DashScopeChatModel}（同包 @Component @Primary），
     * 不是 Spring AI 的 OpenAiChatModel —— 因为专属实例的 compatible-mode 返回的不是标准 OpenAI 格式，
     * OpenAiChatModel 解析不了，必须用自定义 ChatModel 适配。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
    public ChatClient ragChatClient(
            @Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel,
            QuestionAnswerAdvisor questionAnswerAdvisor) {
        // ★ 不再挂 QuestionAnswerAdvisor，RAG 检索改为 AiTriageService 手动管理
        // 原因：Spring AI 1.0.0 的 QuestionAnswerAdvisor 用 promptTemplate 渲染结果
        // 替换 user message 时不会追加用户原始问题，导致症状文本被抹掉、模型输出空 JSON。
        // AiTriageService.callTriageModel() 现在自己 vectorStore.similaritySearch()
        // 拿到结果，直接渲染进 triage-prompt.st 的 {ragContext} 占位符。
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem("你是熙康云医院的专业医疗分诊AI助手，根据患者症状给出科室、紧迫度、红旗征等结构化判断。"
                        + "请优先基于医院知识库中的真实信息做推荐。")
                .build();
    }
}
