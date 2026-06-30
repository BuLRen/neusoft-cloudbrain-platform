package com.xikang.physician.copilot;

import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/physician/ai/copilot")
@RequiredArgsConstructor
public class PhysicianCopilotController {

    private final PhysicianCopilotService copilotService;

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam Long registerId) {
        return Result.success(copilotService.getHistory(registerId));
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory(@RequestParam Long registerId) {
        copilotService.clearHistory(registerId);
        return Result.success();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));

        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            Map<String, Object> meta = copilotService.chat(registerId, message, chunk -> {
                try {
                    if (chunk != null && !chunk.isEmpty()) {
                        emitter.send(SseEmitter.event().name("token").data(chunk));
                    }
                } catch (IOException ex) {
                    log.warn("Copilot SSE send failed", ex);
                }
            });
            emitter.send(SseEmitter.event().name("meta").data(meta));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Copilot chat endpoint failed", ex);
            try {
                emitter.send(SseEmitter.event().name("error").data(
                    ex.getMessage() != null ? ex.getMessage() : "AI 助手暂不可用，请稍后重试"));
                emitter.complete();
            } catch (IOException ioEx) {
                emitter.completeWithError(ioEx);
            }
        }
        return emitter;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
