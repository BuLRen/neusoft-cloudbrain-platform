package com.xikang.ai.triage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百度短语音识别服务（HTTP API）
 *
 * 文档：https://cloud.baidu.com/doc/SPEECH/s/jlbxejt2i
 *
 * 流程：
 * 1. OAuth 获取 Access Token (client_credentials 模式)
 * 2. 调用短语音识别 API (server_api)
 */
@Slf4j
@Service
public class BaiduAsrService {

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String secretKey;
    private final OkHttpClient httpClient;

    /** Access Token 缓存 */
    private String cachedToken = null;
    private long tokenExpireTime = 0;

    public BaiduAsrService(
            ObjectMapper objectMapper,
            @Value("${baidu.asr.api-key:}") String apiKey,
            @Value("${baidu.asr.secret-key:}") String secretKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        log.info("[BaiduASR] 短语音识别服务初始化: apiKey={}, secretKey={}",
                maskKey(apiKey), maskKey(secretKey));
    }

    /**
     * 隐藏 key 前 8 位的掩码显示，key 缺失或过短时返回占位符。
     */
    private static String maskKey(String key) {
        if (key == null || key.isEmpty()) return "(empty)";
        if (key.length() <= 8) return "***";
        return key.substring(0, 8) + "...";
    }

    /**
     * 识别音频（短语音识别）
     * @param pcmData PCM 16kHz 16bit 音频数据
     * @param cuid 设备标识
     * @return 转写文本，失败返回 null
     */
    public String recognize(byte[] pcmData, String cuid) {
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            log.error("[BaiduASR] 百度 ASR 未配置 api-key 或 secret-key");
            return null;
        }

        try {
            // 1. 获取 Access Token
            String token = getAccessToken();
            if (token == null) {
                log.error("[BaiduASR] 获取 Access Token 失败");
                return null;
            }

            // 2. 调用短语音识别 API
            String result = doRecognize(pcmData, token, cuid);
            return result;

        } catch (Exception e) {
            log.error("[BaiduASR] 语音识别失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取 Access Token（带缓存）
     */
    private synchronized String getAccessToken() throws IOException {
        // 检查缓存是否有效（提前5分钟过期）
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime - 300000) {
            return cachedToken;
        }

        log.info("[BaiduASR] 获取 Access Token...");

        FormBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", apiKey)
                .add("client_secret", secretKey)
                .build();

        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[BaiduASR] 获取 Token 失败: {}", response);
                return null;
            }

            String responseBody = response.body().string();
            log.debug("[BaiduASR] Token 响应: {}", responseBody);

            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("access_token")) {
                cachedToken = json.get("access_token").asText();
                // 提前 5 分钟过期，确保有足够时间刷新
                tokenExpireTime = System.currentTimeMillis() + (json.get("expires_in").asLong() * 1000);
                log.info("[BaiduASR] 获取 Token 成功，有效期: {} 秒", json.get("expires_in").asLong());
                return cachedToken;
            } else {
                log.error("[BaiduASR] Token 响应无 access_token: {}", responseBody);
                return null;
            }
        }
    }

    /**
     * 调用短语音识别 API
     */
    private String doRecognize(byte[] pcmData, String token, String cuid) throws IOException {
        // Base64 编码音频数据
        String audioData = Base64.getEncoder().encodeToString(pcmData);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("format", "pcm");
        requestBody.put("rate", 16000);
        requestBody.put("channel", 1);
        requestBody.put("cuid", cuid != null ? cuid : "default-cuid");
        requestBody.put("token", token);
        requestBody.put("len", pcmData.length);
        requestBody.put("speech", audioData);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.info("[BaiduASR] 发送识别请求: {} bytes", pcmData.length);

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://vop.baidu.com/server_api")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[BaiduASR] 识别请求失败: {}", response);
                return null;
            }

            String responseBody = response.body().string();
            log.debug("[BaiduASR] 识别响应: {}", responseBody);

            return parseRecognitionResult(responseBody);
        }
    }

    /**
     * 解析识别结果
     */
    private String parseRecognitionResult(String responseBody) throws IOException {
        JsonNode json = objectMapper.readTree(responseBody);

        int errNo = json.has("err_no") ? json.get("err_no").asInt() : -1;
        String errMsg = json.has("err_msg") ? json.get("err_msg").asText() : "未知错误";

        if (errNo != 0) {
            log.error("[BaiduASR] 识别错误: err_no={}, err_msg={}", errNo, errMsg);
            return null;
        }

        // 解析结果
        StringBuilder result = new StringBuilder();
        if (json.has("result")) {
            JsonNode resultNode = json.get("result");
            if (resultNode.isArray()) {
                for (JsonNode item : resultNode) {
                    result.append(item.asText());
                }
            }
        }

        String text = result.toString().trim();
        log.info("[BaiduASR] 识别成功: {}", text);
        return text;
    }

    /**
     * 检查是否已配置
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && secretKey != null && !secretKey.isEmpty();
    }
}
