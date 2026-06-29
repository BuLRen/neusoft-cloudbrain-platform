package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.dto.DiseaseAiSearchRequest;
import com.xikang.physician.dto.DrugAiSearchRequest;
import com.xikang.physician.service.DiseaseAiSearchService;
import com.xikang.physician.service.DrugAiSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoints for Dify workflow HTTP nodes (Bearer {@code INTERNAL_AI_TOKEN}).
 */
@RestController
@RequestMapping("/api/physician/internal")
public class PhysicianInternalController {

    private final DiseaseAiSearchService diseaseAiSearchService;
    private final DrugAiSearchService drugAiSearchService;

    public PhysicianInternalController(
        DiseaseAiSearchService diseaseAiSearchService,
        DrugAiSearchService drugAiSearchService
    ) {
        this.diseaseAiSearchService = diseaseAiSearchService;
        this.drugAiSearchService = drugAiSearchService;
    }

    /**
     * W4 workflow: search candidate diseases from local ICD catalog.
     */
    @PostMapping("/diseases/ai-search")
    public Result<List<Map<String, Object>>> searchDiseasesForAi(@RequestBody DiseaseAiSearchRequest request) {
        List<Map<String, Object>> data = diseaseAiSearchService.search(request);
        return Result.success(data);
    }

    /**
     * W5 workflow: search candidate drugs from local drug_info catalog.
     */
    @PostMapping("/drugs/ai-search")
    public Result<Map<String, Object>> searchDrugsForAi(@RequestBody DrugAiSearchRequest request) {
        List<Map<String, Object>> candidates = drugAiSearchService.search(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidates", candidates);
        return Result.success(data);
    }
}
