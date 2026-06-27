package com.xikang.ai.triage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG 配置类 —— 仅在 {@code spring.ai.rag.enabled=true} 时装配。
 *
 * <p>启用前提：服务器已装 pgvector 扩展（详见 docs/server-install-pgvector.md）。
 * 未启用时（默认），整套 RAG Bean 不装配，应用走 DeepSeek + 原 prompt 流程。
 *
 * <p>本配置启用时装配：
 * <ul>
 *   <li>通义千问 Chat 模型（{@link OpenAiChatModel}，走 OpenAI 兼容路径）</li>
 *   <li>通义千问 Embedding 模型（{@link DashScopeEmbeddingModel}，走 DashScope 原生路径，
 *       因为专属实例的 Embedding 不在兼容路径下）</li>
 *   <li>{@link PgVectorStore}（向 ai_triage_knowledge 表读写向量）</li>
 *   <li>{@link QuestionAnswerAdvisor}（由 AiConfig 挂到 ChatClient 上）</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
public class RagConfig {

    // 注意：通义千问 Chat 模型现在由同包的 DashScopeChatModel（@Component @Primary）提供。
    // 之前在这里定义的 OpenAiChatModel dashscopeChatModel Bean 已删除，
    // 因为专属实例的 compatible-mode 返回的不是标准 OpenAI 格式，OpenAiChatModel 解析不了。

    /**
     * PgVector 向量库 Bean。
     *
     * <p><b>关键：{@code initializeSchema(false)}</b>。
     * Spring AI 1.0.0 的 PgVectorStore 在 initializeSchema=true 时会硬编码执行
     * {@code CREATE EXTENSION "uuid-ossp"}，但宝塔装的 PG 16 没有该扩展。
     * 改为手动建表（见 {@link #ensureSchema(JdbcTemplate)}），用 PG 13+ 内置的
     * {@code gen_random_uuid()} 代替 uuid-ossp 的 uuid_generate_v4()。
     *
     * <p>注入的 embeddingModel 是 {@link DashScopeEmbeddingModel}（同包 @Component @Primary）。
     */
    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, DashScopeEmbeddingModel embeddingModel) {
        // 先确保表结构存在（替代 Spring AI 自动建表）
        ensureSchema(jdbcTemplate);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .schemaName("public")
                .vectorTableName("ai_triage_knowledge")
                .initializeSchema(false)
                .build();
    }

    /**
     * 手动建表 —— 绕过 Spring AI 1.0.0 的 uuid-ossp 硬编码依赖。
     *
     * <p>字段名与 Spring AI PgVectorStore 期望的 schema 完全一致：
     * <ul>
     *   <li>{@code id uuid}：主键，默认 gen_random_uuid()（PG 13+ 内置，无需 uuid-ossp）</li>
     *   <li>{@code content text}：原文</li>
     *   <li>{@code metadata jsonb}：元数据（Spring AI 用 jsonb）</li>
     *   <li>{@code embedding vector(1024)}：1024 维向量（text-embedding-v3）</li>
     * </ul>
     *
     * <p>建表前已由 DBA 执行过 {@code CREATE EXTENSION vector}（见 docs/server-install-pgvector.md）。
     */
    private void ensureSchema(JdbcTemplate jdbcTemplate) {
        // 1. 建表（IF NOT EXISTS 幂等，重复启动不会报错）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS public.ai_triage_knowledge (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    content text,
                    metadata jsonb,
                    embedding vector(1024)
                )
                """);

        // 2. HNSW 索引（cosine 距离）—— Spring AI initializeSchema=false 时不会自动建
        //    已存在时不报错（IF NOT EXISTS）
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ai_triage_knowledge_embedding_idx
                ON public.ai_triage_knowledge
                USING hnsw (embedding vector_cosine_ops)
                """);

        log.info("[RagConfig] ai_triage_knowledge 表已就绪（手动建表，未依赖 uuid-ossp）");
    }

    /**
     * RAG Advisor —— 把检索到的医院知识片段自动塞进 prompt 的 question_answer_context 占位符。
     *
     * <p>使用自定义中文 promptTemplate，避免默认英文模板影响结构化输出。
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        // ★ Spring AI 1.0.0 QuestionAnswerAdvisor 的 promptTemplate 行为：
        //   - {question_answer_context}：被检索到的知识库文档替换（框架自动填充）
        //   - 用户原始问题：框架会从 chatClient.prompt().user(...) 抽取，自动追加到模板渲染结果之后
        //   - ⚠️ 不要再加其他占位符（如 {question}），否则会抛 "Missing variable names are: [xxx]"
        //
        // 错误历史：
        //   1. 最早没加 {question} → 模型看不到症状 → "患者未提供症状信息"（其实是幻觉式拒绝）
        //   2. 加了 {question} → IllegalStateException: Missing variable names are: [question]
        //   3. 现状：只保留 {question_answer_context}，症状由框架自动追加到模板后
        PromptTemplate pt = new PromptTemplate("""
                以下是本院知识库中与患者症状相关的真实信息：
                {question_answer_context}

                请结合上述本院真实数据和患者症状做推荐（科室ID必须从知识库中选择）。
                """);
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(8)
                        // 阈值从 0.7 降到 0.5：患者口语（"摔了一跤/骨头疼"）与知识库书面表述
                        // （"骨折/扭伤/外伤"）余弦相似度多落在 0.55~0.68 区间，0.7 会把这类
                        // 命中全部过滤掉，导致 advisor 注入空上下文，模型退化为"安全默认内科"。
                        // 0.5 在 text-embedding-v3 上经验值：能召回语义相关文档，噪声仍在可控范围。
                        .similarityThreshold(0.5)
                        .build())
                .promptTemplate(pt)
                .build();
    }
}
