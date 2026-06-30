package com.xikang.ai.consult.controller;

import com.xikang.ai.consult.service.PreConsultService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * AI 预问诊控制器（SSE 流式）
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/consult")
@RequiredArgsConstructor
public class AiConsultController {

    private final PreConsultService preConsultService;

    /**
     * 开始预问诊，SSE 流式返回首条 AI 提问
     * POST /api/ai/consult/preconsult/start
     * body: { "registerId": 100, "patientId": 1 }
     */
    @PostMapping(value = "/preconsult/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startPreConsult(@RequestBody Map<String, Object> body) {
        Integer registerId = toInt(body.get("registerId"));
        Integer patientId = toInt(body.get("patientId"));
        // triageSessionId：前端透传的导诊会话ID，用于精确关联本次导诊，避免历史导诊污染预问诊
        String triageSessionId = body.get("triageSessionId") == null ? null : body.get("triageSessionId").toString();

        SseEmitter emitter = new SseEmitter(60_000L);
        // 关键：在 controller 自己捕获并把错误作为 SSE 事件推回前端，
        // 避免进入 ResponseBodyEmitterReturnValueHandler.sendInternal 的 null-data 路径
        try {
            Map<String, Object> meta = preConsultService.start(registerId, patientId, triageSessionId, chunk -> {
                try {
                    if (chunk != null && !chunk.isEmpty()) {
                        emitter.send(SseEmitter.event().name("token").data(chunk));
                    }
                } catch (IOException e) {
                    log.warn("SSE 发送失败", e);
                }
            });
            if (meta != null) {
                emitter.send(SseEmitter.event().name("meta").data(meta));
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("开始预问诊失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(
                    e.getMessage() != null ? e.getMessage() : "AI 服务暂不可用，请稍后再试"));
                emitter.complete();
            } catch (IOException ioEx) {
                emitter.completeWithError(ioEx);
            }
        }
        return emitter;
    }

    /**
     * 患者回复，SSE 流式返回 AI 下一句
     * POST /api/ai/consult/preconsult/reply
     * body: { "sessionUuid": "...", "answer": "头痛" }
     */
    @PostMapping(value = "/preconsult/reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reply(@RequestBody Map<String, Object> body) {
        String sessionUuid = (String) body.get("sessionUuid");
        String answer = (String) body.get("answer");

        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            Map<String, Object> meta = preConsultService.reply(sessionUuid, answer, chunk -> {
                try {
                    if (chunk != null && !chunk.isEmpty()) {
                        emitter.send(SseEmitter.event().name("token").data(chunk));
                    }
                } catch (IOException e) {
                    log.warn("SSE 发送失败", e);
                }
            });
            if (meta != null) {
                emitter.send(SseEmitter.event().name("meta").data(meta));
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("预问诊回复失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(
                    e.getMessage() != null ? e.getMessage() : "AI 服务暂不可用，请稍后再试"));
                emitter.complete();
            } catch (IOException ioEx) {
                emitter.completeWithError(ioEx);
            }
        }
        return emitter;
    }

    /**
     * 结束预问诊，生成结构化病历
     * POST /api/ai/consult/preconsult/finish
     * body: { "sessionUuid": "..." }
     */
    @PostMapping("/preconsult/finish")
    public Result<Map<String, Object>> finish(@RequestBody Map<String, Object> body) {
        String sessionUuid = (String) body.get("sessionUuid");
        Map<String, Object> summary = preConsultService.finish(sessionUuid);
        return Result.success(summary);
    }

    /**
     * 获取某挂号下的预问诊会话（前端进入时拉历史）
     * GET /api/ai/consult/preconsult/session/{registerId}
     */
    @GetMapping("/preconsult/session/{registerId}")
    public Result<Map<String, Object>> getSession(@PathVariable Integer registerId) {
        return Result.success(preConsultService.getSession(registerId));
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }
}
