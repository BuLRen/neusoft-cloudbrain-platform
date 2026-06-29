package com.xikang.ai.catalog.service;

import com.xikang.ai.catalog.dto.DrugAiSearchRequest;
import com.xikang.ai.catalog.mapper.DrugCatalogMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DrugAiSearchService {

    private static final int DEFAULT_LIMIT = 40;
    private static final int MAX_LIMIT = 80;

    private final DrugCatalogMapper drugCatalogMapper;

    public DrugAiSearchService(DrugCatalogMapper drugCatalogMapper) {
        this.drugCatalogMapper = drugCatalogMapper;
    }

    public List<Map<String, Object>> search(DrugAiSearchRequest request) {
        int limit = normalizeLimit(request.getLimit());
        List<String> drugKeywords = cleanKeywords(request.getDrugKeywords());
        List<String> genericKeywords = cleanKeywords(request.getGenericKeywords());
        List<String> categoryKeywords = cleanKeywords(request.getCategoryKeywords());
        List<String> indicationKeywords = cleanKeywords(request.getIndicationKeywords());
        List<String> negativeKeywords = cleanKeywords(request.getNegativeKeywords());

        if (drugKeywords.isEmpty() && genericKeywords.isEmpty()
            && categoryKeywords.isEmpty() && indicationKeywords.isEmpty()) {
            return List.of();
        }

        int fetchLimit = Math.min(Math.max(limit * 3, limit), 200);
        List<Map<String, Object>> rawRows = drugCatalogMapper.searchDrugsForAi(
            drugKeywords,
            genericKeywords,
            categoryKeywords,
            indicationKeywords,
            fetchLimit
        );

        Map<Long, ScoredDrug> scoredById = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            ScoredDrug item = scoreRow(row, drugKeywords, genericKeywords, categoryKeywords, indicationKeywords, negativeKeywords);
            if (item == null) {
                continue;
            }
            Long id = item.drugId();
            ScoredDrug existing = scoredById.get(id);
            if (existing == null || item.score() > existing.score()) {
                scoredById.put(id, item);
            }
        }

        return scoredById.values().stream()
            .sorted(Comparator.comparingInt(ScoredDrug::score).reversed())
            .limit(limit)
            .map(ScoredDrug::toCandidate)
            .toList();
    }

    private static ScoredDrug scoreRow(
        Map<String, Object> row,
        List<String> drugKeywords,
        List<String> genericKeywords,
        List<String> categoryKeywords,
        List<String> indicationKeywords,
        List<String> negativeKeywords
    ) {
        String drugName = str(row.get("drugName"));
        String genericName = str(row.get("genericName"));
        String category = str(row.get("category"));
        String instructions = str(row.get("instructions"));
        String contraindications = str(row.get("contraindications"));
        String searchable = (drugName + " " + genericName + " " + category + " " + instructions).toLowerCase(Locale.ROOT);

        for (String negative : negativeKeywords) {
            if (!negative.isEmpty() && searchable.contains(negative.toLowerCase(Locale.ROOT))) {
                return null;
            }
            if (!negative.isEmpty() && contraindications.toLowerCase(Locale.ROOT).contains(negative.toLowerCase(Locale.ROOT))) {
                return null;
            }
        }

        int score = 0;
        for (String keyword : drugKeywords) {
            if (drugName.contains(keyword)) {
                score += 40;
            } else if (searchable.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 20;
            }
        }
        for (String keyword : genericKeywords) {
            if (genericName.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 25;
            }
        }
        for (String keyword : categoryKeywords) {
            if (category.contains(keyword)) {
                score += 15;
            }
        }
        for (String keyword : indicationKeywords) {
            if (instructions.contains(keyword) || drugName.contains(keyword)) {
                score += 18;
            } else if (searchable.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 8;
            }
        }

        if (score <= 0) {
            score = 5;
        }

        return new ScoredDrug(toLong(row.get("drugId")), row, score);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static List<String> cleanKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String value = keyword.trim();
            if (!value.isEmpty() && !cleaned.contains(value)) {
                cleaned.add(value);
            }
        }
        return cleaned;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ScoredDrug(Long drugId, Map<String, Object> row, int score) {
        Map<String, Object> toCandidate() {
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("drugId", drugId);
            candidate.put("drugCode", row.get("drugCode"));
            candidate.put("drugName", row.get("drugName"));
            candidate.put("specification", row.getOrDefault("specification", row.get("drugFormat")));
            candidate.put("category", row.getOrDefault("category", row.get("drugType")));
            candidate.put("price", row.getOrDefault("price", row.get("drugPrice")));
            candidate.put("instructions", row.get("instructions"));
            candidate.put("contraindications", row.get("contraindications"));
            candidate.put("matchScore", score);
            return candidate;
        }
    }
}
