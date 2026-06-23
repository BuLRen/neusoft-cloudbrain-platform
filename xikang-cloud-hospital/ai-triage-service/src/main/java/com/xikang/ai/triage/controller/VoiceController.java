package com.xikang.ai.triage.controller;

import com.xikang.ai.triage.service.BaiduAsrService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 语音识别控制器
 * 支持两种模式：
 * 1. REST 上传：前端录音结束后发送整个音频文件
 * 2. WebSocket 流式：前端实时流式发送音频（见 /ws/voice）
 */
@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final BaiduAsrService baiduAsrService;

    /**
     * 上传音频文件进行转写（短音频，非流式）
     *
     * @param audio  音频文件（支持 pcm/wav/opus格式，建议16000Hz）
     * @param format 音频格式（pcm/wav/opus），默认 pcm
     * @return 转写文本
     */
    @PostMapping("/transcribe")
    public Result<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "format", defaultValue = "pcm") String format) {

        if (audio.isEmpty()) {
            return Result.error("音频文件不能为空");
        }

        try {
            byte[] audioData = audio.getBytes();
            log.info("[Voice] 收到音频文件: size={} bytes, format={}", audioData.length, format);

            if (audioData.length < 1000) {
                return Result.error("音频太短，请至少说一句话");
            }

            return Result.error("语音转写请使用 WebSocket 模式（/ws/voice），REST API 已废弃");
        } catch (Exception e) {
            log.error("[Voice] 转写异常", e);
            return Result.error("转写失败: " + e.getMessage());
        }
    }

    /**
     * 语音识别健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean configured = baiduAsrService.isConfigured();
        return Result.success(Map.of(
                "status", configured ? "configured" : "not_configured",
                "service", "baidu_asr",
                "message", configured ? "百度ASR已配置" : "请检查 BAIDU_ASR_APP_ID / API_KEY 配置"
        ));
    }
}