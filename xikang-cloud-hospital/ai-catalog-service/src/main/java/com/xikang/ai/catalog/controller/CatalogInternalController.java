package com.xikang.ai.catalog.controller;

import com.xikang.ai.catalog.dto.DiseaseAiSearchRequest;
import com.xikang.ai.catalog.dto.DrugAiSearchRequest;
import com.xikang.ai.catalog.service.DiseaseAiSearchService;
import com.xikang.ai.catalog.service.DrugAiSearchService;
import com.xikang.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoints for Dify workflow HTTP nodes (Bearer {@code INTERNAL_AI_TOKEN}).
 * Paths kept identical to former physician-service endpoints for minimal Dify migration.
 */
@RestController
@RequestMapping("/api/physician/internal")
public class CatalogInternalController {

    private final DiseaseAiSearchService diseaseAiSearchService;
    private final DrugAiSearchService drugAiSearchService;

    public CatalogInternalController(
        DiseaseAiSearchService diseaseAiSearchService,
        DrugAiSearchService drugAiSearchService
    ) {
        this.diseaseAiSearchService = diseaseAiSearchService;
        this.drugAiSearchService = drugAiSearchService;
    }

    @PostMapping("/diseases/ai-search")
    public Result<List<Map<String, Object>>> searchDiseasesForAi(@RequestBody DiseaseAiSearchRequest request) {
        List<Map<String, Object>> data = diseaseAiSearchService.search(request);
        return Result.success(data);
    }

    @PostMapping("/drugs/ai-search")
    public Result<Map<String, Object>> searchDrugsForAi(@RequestBody DrugAiSearchRequest request) {
        List<Map<String, Object>> candidates = drugAiSearchService.search(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidates", candidates);
        return Result.success(data);
    }
}
