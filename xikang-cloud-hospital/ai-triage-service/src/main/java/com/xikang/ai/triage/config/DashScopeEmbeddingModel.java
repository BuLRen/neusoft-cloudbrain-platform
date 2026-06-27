package com.xikang.ai.triage.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义通义千问 Embedding 模型 —— 走 DashScope 原生 API 路径。
 *
 * <p>背景：阿里云百炼"专属实例"的 Embedding 服务只在 DashScope 原生路径
 * （{@code /api/v1/services/embeddings/text-embedding/text-embedding}）下提供，
 * 不在 OpenAI 兼容路径（{@code /compatible-mode/v1/embeddings}）下提供。
 * Spring AI 1.0 的 {@code OpenAiEmbeddingModel} 走的是兼容路径，调不通专属实例。
 *
 * <p>本类通过实现 Spring AI 的 {@link AbstractEmbeddingModel} 接口，
 * 把请求转发到 DashScope 原生路径，让 RAG 流程能正常使用专属实例的 Embedding 服务。
 *
 * <p>仅在 {@code spring.ai.rag.enabled=true} 时装配。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
public class DashScopeEmbeddingModel extends AbstractEmbeddingModel {

    private static final String EMBEDDING_PATH = "/api/v1/services/embeddings/text-embedding/text-embedding";

    /** text-embedding-v3 的固定维度。重写 dimensions() 直接返回，避免 AbstractEmbeddingModel 启动时隐式打一次 API 探测。 */
    private static final int DIMENSIONS = 1024;

    /**
     * DashScope 专属实例 Embedding 单次请求最多 10 条文本。
     * <p>但实测一批 10 条响应可能 30s+，靠近超时边界。
     * <p>取 5 —— 响应更快（~10s），远离超时阈值，整体吞吐反而更稳。
     */
    private static final int BATCH_SIZE = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modelName;

    public DashScopeEmbeddingModel(
            @Value("${spring.ai.dashscope.embedding-base-url}") String baseUrl,
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${spring.ai.dashscope.embedding-model}") String embeddingModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.modelName = embeddingModel;

        // ★ 必须用 JdkClientHttpRequestFactory，不能用 SimpleClientHttpRequestFactory
        //
        // 原因：SimpleClientHttpRequestFactory 底层是 JDK 的 HttpURLConnection，
        // 它的 setReadTimeout 只对"首次读到字节"生效——一旦服务器开始返回数据
        // （哪怕 SSL 握手时回了 1 字节），就不再计算超时，会无限挂起。
        // 实测 DashScope 慢响应时 SimpleClientHttpRequestFactory 会挂 10 分钟不报错。
        //
        // JdkClientHttpRequestFactory 底层是 java.net.http.HttpClient（Java 11+ 内置），
        // 超时机制可靠：响应超时即抛 HttpTimeoutException，不会无限挂。
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));

        // baseUrl 是裸 host（如 https://ws-xxx.maas.aliyuncs.com），不带 /compatible-mode
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[DashScopeEmbeddingModel] 初始化完成：baseUrl={}, model={}, dimensions={}",
                baseUrl, modelName, DIMENSIONS);
    }

    /**
     * DashScope 专属实例单次请求最多 10 条文本，超过会返回 400：
     * {@code batch size is invalid, it should not be larger than 10}
     *
     * <p>因此把输入按 {@link #BATCH_SIZE} 分批，逐批调用，再合并结果。
     * Embedding 的 index 必须按原始输入顺序连续编号（Spring AI 上层依赖该下标取回结果）。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        if (inputs.isEmpty()) {
            return new EmbeddingResponse(List.of(), new EmbeddingResponseMetadata());
        }

        try {
            List<Embedding> all = new ArrayList<>(inputs.size());
            int globalIndex = 0;

            for (int from = 0; from < inputs.size(); from += BATCH_SIZE) {
                int to = Math.min(from + BATCH_SIZE, inputs.size());
                List<String> batch = inputs.subList(from, to);

                List<float[]> vectors = callBatch(batch);
                for (float[] v : vectors) {
                    all.add(new Embedding(v, globalIndex++));
                }
            }
            return new EmbeddingResponse(all, new EmbeddingResponseMetadata());
        } catch (Exception e) {
            log.error("[DashScopeEmbeddingModel] 调用 Embedding 失败，inputs size={}", inputs.size(), e);
            throw new RuntimeException("DashScope Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用单批（≤BATCH_SIZE 条）。
     * <p>抽出来便于上层循环复用，也便于出错时定位是哪一批失败。
     * <p>打日志记录每批耗时 —— DashScope 慢响应时这是定位瓶颈的唯一手段。
     */
    private List<float[]> callBatch(List<String> batch) {
        Map<String, Object> body = Map.of(
                "model", modelName,
                "input", Map.of("texts", batch)
        );
        long t0 = System.currentTimeMillis();
        String responseJson = restClient.post()
                .uri(EMBEDDING_PATH)
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("[DashScopeEmbeddingModel] 一批 {} 条耗时 {} ms",
                batch.size(), System.currentTimeMillis() - t0);

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode embeddingsNode = root.path("output").path("embeddings");

            List<float[]> vectors = new ArrayList<>(embeddingsNode.size());
            for (JsonNode item : embeddingsNode) {
                vectors.add(parseFloatArray(item.path("embedding")));
            }
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException(
                        "Embedding 数量与输入不一致：expected=" + batch.size() + ", actual=" + vectors.size());
            }
            return vectors;
        } catch (Exception e) {
            throw new RuntimeException("解析 Embedding 响应失败: " + e.getMessage()
                    + ", raw=" + responseJson, e);
        }
    }

    @Override
    public float[] embed(Document document) {
        // 单文档 embedding，直接走 call() 然后取第一条结果
        EmbeddingResponse response = call(new EmbeddingRequest(
                List.of(document.getFormattedContent()),
                null));
        return response.getResult().getOutput();
    }

    /**
     * 直接返回固定维度，避免父类 {@link AbstractEmbeddingModel#dimensions()}
     * 在首次调用时隐式打一次真实 Embedding API 探测维度（消耗 token、拖慢启动）。
     * 维度值需与 PgVectorStore 的 dimensions() 配置保持一致。
     */
    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 把 JSON 数组节点解析为 float[]。
     */
    private float[] parseFloatArray(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return new float[0];
        }
        float[] result = new float[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            result[i] = (float) arrayNode.get(i).asDouble();
        }
        return result;
    }
}
