package com.xikang.ai.triage.rag;

import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库管理接口 —— 仅在 {@code spring.ai.rag.enabled=true} 时注册。
 *
 * <p>当 RAG 关闭时，本 Controller 不装配，调用 {@code /api/ai/triage/kb/*} 返回 404。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/triage/kb")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.rag.enabled", havingValue = "true")
public class KnowledgeAdminController {

    private final KnowledgeIngestService ingestService;

    /**
     * 全量重建知识库（先删后插）。
     * <p>典型用法：医院新增科室/医生后调用一次。
     */
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        int count = ingestService.reload();
        return Result.success(Map.of("documents", count));
    }

    /**
     * 健康检查：确认 RAG 是否启用、知识库是否可用。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "ragEnabled", true,
                "message", "RAG 已启用，可调用 /reload 入库知识"
        ));
    }
}
