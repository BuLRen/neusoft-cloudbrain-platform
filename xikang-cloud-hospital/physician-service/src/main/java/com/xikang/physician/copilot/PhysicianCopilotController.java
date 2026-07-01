package com.xikang.physician.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.physician.ai.DifyWorkflowException;
import com.xikang.physician.context.PhysicianAuthContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/api/physician/ai/copilot")
public class PhysicianCopilotController {

    private final PhysicianCopilotService copilotService;
    private final ObjectMapper objectMapper;
    private final Executor copilotChatExecutor;

    public PhysicianCopilotController(
        PhysicianCopilotService copilotService,
        ObjectMapper objectMapper,
        @Qualifier("copilotChatExecutor") Executor copilotChatExecutor
    ) {
        this.copilotService = copilotService;
        this.objectMapper = objectMapper;
        this.copilotChatExecutor = copilotChatExecutor;
    }

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

    @GetMapping("/confirm-completions")
    public Result<List<Map<String, Object>>> confirmCompletions(
        @RequestParam Long registerId,
        @RequestParam Long sessionId
    ) {
        try {
            return Result.success(copilotService.listConfirmCompletions(registerId, sessionId));
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

    /**
     * SSE 流式对话：立即返回 SseEmitter，在后台线程中转发 Dify/Spring AI 的 token 与 thought 事件。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
        @RequestBody Map<String, Object> body,
        HttpServletResponse response
    ) {
        Long registerId = toLong(body.get("registerId"));
        Long sessionId = toLong(body.get("sessionId"));
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));

        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.warn("Copilot SSE client disconnected", ex));

        // 身份上下文是 ThreadLocal，SSE 在后台线程执行，必须在此捕获并在任务线程中重建，
        // 否则管理员/医生身份会丢失，导致 assertRegisterAccess 误判为「无权访问该患者」。
        PhysicianAuthContext.Context authContext = PhysicianAuthContext.get();
        copilotChatExecutor.execute(() -> runChatStream(emitter, authContext, registerId, sessionId, message));
        return emitter;
    }

    private void runChatStream(
        SseEmitter emitter,
        PhysicianAuthContext.Context authContext,
        Long registerId,
        Long sessionId,
        String message
    ) {
        PhysicianAuthContext.set(authContext);
        try {
            Map<String, Object> meta = copilotService.chat(
                registerId,
                sessionId,
                message,
                chunk -> sendToken(emitter, chunk),
                thought -> sendThought(emitter, thought)
            );
            sendJsonEvent(emitter, "meta", meta);
            emitter.complete();
        } catch (Exception ex) {
            log.error("Copilot chat stream failed", ex);
            try {
                sendEvent(emitter, "error", ex.getMessage() != null
                    ? ex.getMessage()
                    : "AI 助手暂不可用，请稍后重试");
                emitter.complete();
            } catch (IOException ioEx) {
                emitter.completeWithError(ioEx);
            }
        } finally {
            PhysicianAuthContext.clear();
        }
    }

    private void sendToken(SseEmitter emitter, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        try {
            sendEvent(emitter, "token", chunk);
        } catch (IOException ex) {
            log.warn("Copilot SSE token send failed", ex);
        }
    }

    private void sendThought(SseEmitter emitter, Map<String, Object> thought) {
        try {
            sendJsonEvent(emitter, "thought", thought);
        } catch (IOException ex) {
            log.warn("Copilot SSE thought send failed", ex);
        }
    }

    private void sendJsonEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        sendEvent(emitter, eventName, objectMapper.writeValueAsString(payload));
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
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

    @PostMapping("/prepare-action")
    public Result<Map<String, Object>> prepareAction(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        Long sessionId = toLong(body.get("sessionId"));
        String actionType = body.get("actionType") == null ? "" : String.valueOf(body.get("actionType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
        try {
            return Result.success(copilotService.prepareCopilotAction(registerId, sessionId, actionType, payload));
        } catch (Exception ex) {
            log.error("Copilot prepare-action failed", ex);
            return Result.error(ex.getMessage() != null ? ex.getMessage() : "生成确认令牌失败");
        }
    }

    @PostMapping("/confirm-action")
    public Result<Map<String, Object>> confirmAction(@RequestBody Map<String, Object> body) {
        Long registerId = toLong(body.get("registerId"));
        Long sessionId = toLong(body.get("sessionId"));
        String confirmationToken = body.get("confirmationToken") == null
            ? ""
            : String.valueOf(body.get("confirmationToken"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadOverride = body.get("payloadOverride") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : null;
        try {
            return Result.success(copilotService.confirmCopilotAction(
                registerId, sessionId, confirmationToken, payloadOverride));
        } catch (Exception ex) {
            log.error("Copilot confirm-action failed", ex);
            return Result.error(ex.getMessage() != null ? ex.getMessage() : "确认提交失败");
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
