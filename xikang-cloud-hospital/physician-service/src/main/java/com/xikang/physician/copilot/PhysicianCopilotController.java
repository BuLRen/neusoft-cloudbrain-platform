package com.xikang.physician.copilot;

import com.xikang.common.result.Result;
import com.xikang.physician.ai.DifyWorkflowException;
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

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> listSessions(@RequestParam Long registerId) {
        return Result.success(copilotService.listSessions(registerId));
    }

    @PostMapping("/sessions")
    public Result<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        try {
            return Result.success(copilotService.createSession(registerId, title));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
        @PathVariable Long sessionId,
        @RequestParam Long registerId
    ) {
        try {
            copilotService.deleteSession(registerId, sessionId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PatchMapping("/sessions/{sessionId}/title")
    public Result<Void> renameSession(
        @PathVariable Long sessionId,
        @RequestBody Map<String, Object> body
    ) {
        Long registerId = toLong(body.get("registerId"));
        String title = body.get("title") == null ? "" : String.valueOf(body.get("title"));
        try {
            copilotService.renameSession(registerId, sessionId, title);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(
        @RequestParam Long registerId,
        @RequestParam Long sessionId
    ) {
        try {
            return Result.success(copilotService.getHistory(registerId, sessionId));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory(
        @RequestParam Long registerId,
        @RequestParam Long sessionId
    ) {
        try {
            copilotService.clearHistory(registerId, sessionId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        Long sessionId = toLong(body.get("sessionId"));
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));

        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            Map<String, Object> meta = copilotService.chat(registerId, sessionId, message, chunk -> {
                try {
                    if (chunk != null && !chunk.isEmpty()) {
                        emitter.send(SseEmitter.event().name("token").data(chunk));
                    }
                } catch (IOException ex) {
                    log.warn("Copilot SSE send failed", ex);
                }
            }, thought -> {
                try {
                    emitter.send(SseEmitter.event().name("thought").data(thought));
                } catch (IOException ex) {
                    log.warn("Copilot SSE thought send failed", ex);
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

    @PostMapping("/run-action")
    public Result<Map<String, Object>> runAction(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        String actionType = body.get("actionType") == null ? "" : String.valueOf(body.get("actionType"));
        try {
            return Result.success(copilotService.runCopilotAction(registerId, actionType));
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        } catch (DifyWorkflowException ex) {
            if (ex.isPaused()) {
                return Result.error("AI 工作流需人工介入，当前系统暂不支持，请稍后重试");
            }
            String detail = ex.getMessage();
            if (detail != null && !detail.isBlank()) {
                return Result.error(detail);
            }
            return Result.error("AI 工作流执行失败，请稍后重试");
        } catch (Exception ex) {
            log.error("Copilot run-action failed", ex);
            return Result.error(ex.getMessage() != null ? ex.getMessage() : "操作执行失败");
        }
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
