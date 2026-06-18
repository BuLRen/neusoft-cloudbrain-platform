package com.xikang.ai.triage.controller;

import com.xikang.ai.triage.service.BaiduAsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * 短语音识别 HTTP 接口
 *
 * 前端流程：
 * 1. 录音收集完整的音频数据
 * 2. 调用此接口上传音频
 * 3. 返回转写结果
 */
@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceRecognitionController {

    private final BaiduAsrService baiduAsrService;

    /**
     * 短语音识别接口
     *
     * @param file 音频文件 (PCM 16kHz 16bit)
     * @return 转写结果
     */
    @PostMapping(value = "/recognize", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> recognize(@RequestBody byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "音频数据为空"));
        }

        String cuid = "cuid-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[Voice API] 收到识别请求: {} bytes, cuid={}", audioData.length, cuid);

        String result = baiduAsrService.recognize(audioData, cuid);

        if (result != null && !result.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", result
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "语音识别失败，未返回结果"
            ));
        }
    }

    /**
     * 检查服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "service", "baidu-asr",
                "configured", baiduAsrService.isConfigured()
        ));
    }
}