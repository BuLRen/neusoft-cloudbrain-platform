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
import java.util.Set;

@Service
public class DrugAiSearchService {

    private static final int DEFAULT_LIMIT = 40;
    private static final int MAX_LIMIT = 80;
    private static final int PER_DRUG_KEYWORD_LIMIT = 40;
    private static final int PER_GENERIC_KEYWORD_LIMIT = 30;
    private static final int PER_CATEGORY_KEYWORD_LIMIT = 25;
    private static final int PER_INDICATION_KEYWORD_LIMIT = 20;

    private static final Set<String> BROAD_CATEGORY_KEYWORDS = Set.of(
        "西药", "中成药", "中药", "生物制品", "化学药", "药品"
    );

    private final DrugCatalogMapper drugCatalogMapper;

    public DrugAiSearchService(DrugCatalogMapper drugCatalogMapper) {
        this.drugCatalogMapper = drugCatalogMapper;
    }

    public List<Map<String, Object>> search(DrugAiSearchRequest request) {
        int limit = normalizeLimit(request.getLimit());
        List<String> drugKeywords = cleanKeywords(request.getDrugKeywords());
        List<String> genericKeywords = cleanKeywords(request.getGenericKeywords());
        List<String> categoryKeywords = filterCategoryKeywords(cleanKeywords(request.getCategoryKeywords()));
        List<String> indicationKeywords = cleanKeywords(request.getIndicationKeywords());
        List<String> negativeKeywords = cleanKeywords(request.getNegativeKeywords());

        if (drugKeywords.isEmpty() && genericKeywords.isEmpty()
            && categoryKeywords.isEmpty() && indicationKeywords.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Object>> rawById = new LinkedHashMap<>();
        collectRows(rawById, fetchByBuckets(drugKeywords, genericKeywords, categoryKeywords, indicationKeywords));

        if (rawById.size() < Math.min(limit, 20)) {
            int fetchLimit = Math.min(Math.max(limit * 3, limit), 200);
            collectRows(rawById, drugCatalogMapper.searchDrugsForAi(
                drugKeywords,
                genericKeywords,
                categoryKeywords,
                indicationKeywords,
                fetchLimit
            ));
        }

        List<ScoredDrug> scored = new ArrayList<>();
        for (Map<String, Object> row : rawById.values()) {
            ScoredDrug item = scoreRow(
                row,
                drugKeywords,
                genericKeywords,
                categoryKeywords,
                indicationKeywords,
                negativeKeywords
            );
            if (item != null) {
                scored.add(item);
            }
        }

        scored.sort(Comparator.comparingInt(ScoredDrug::score).reversed());

        Map<Long, ScoredDrug> deduped = new LinkedHashMap<>();
        for (ScoredDrug item : scored) {
            deduped.putIfAbsent(item.drugId(), item);
            if (deduped.size() >= limit) {
                break;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoredDrug item : deduped.values()) {
            result.add(item.toCandidate());
        }
        return result;
    }

    private List<Map<String, Object>> fetchByBuckets(
        List<String> drugKeywords,
        List<String> genericKeywords,
        List<String> categoryKeywords,
        List<String> indicationKeywords
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String keyword : drugKeywords) {
            rows.addAll(drugCatalogMapper.searchDrugsByNameKeyword(keyword, PER_DRUG_KEYWORD_LIMIT));
        }

        for (String keyword : genericKeywords) {
            rows.addAll(drugCatalogMapper.searchDrugsByGenericKeyword(keyword, PER_GENERIC_KEYWORD_LIMIT));
        }

        for (String keyword : categoryKeywords) {
            rows.addAll(drugCatalogMapper.searchDrugsByCategoryKeyword(keyword, PER_CATEGORY_KEYWORD_LIMIT));
        }

        for (String keyword : indicationKeywords) {
            if (keyword.length() >= 2) {
                rows.addAll(drugCatalogMapper.searchDrugsByIndicationKeyword(keyword, PER_INDICATION_KEYWORD_LIMIT));
            }
        }

        return rows;
    }

    private static void collectRows(Map<Long, Map<String, Object>> target, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("drugId"));
            if (id != null) {
                target.putIfAbsent(id, row);
            }
        }
    }

    private ScoredDrug scoreRow(
        Map<String, Object> row,
        List<String> drugKeywords,
        List<String> genericKeywords,
        List<String> categoryKeywords,
        List<String> indicationKeywords,
        List<String> negativeKeywords
    ) {
        Long drugId = toLong(row.get("drugId"));
        if (drugId == null) {
            return null;
        }

        String drugName = str(row.get("drugName"));
        String genericName = str(row.get("genericName"));
        String category = str(row.get("category"));
        String instructions = str(row.get("instructions"));
        String contraindications = str(row.get("contraindications"));
        String searchable = (drugName + " " + genericName + " " + category + " " + instructions)
            .toLowerCase(Locale.ROOT);
        String nameLower = drugName.toLowerCase(Locale.ROOT);
        String genericLower = genericName.toLowerCase(Locale.ROOT);
        String categoryLower = category.toLowerCase(Locale.ROOT);
        String instructionsLower = instructions.toLowerCase(Locale.ROOT);

        for (String negative : negativeKeywords) {
            if (negative.isBlank()) {
                continue;
            }
            String negativeLower = negative.toLowerCase(Locale.ROOT);
            if (searchable.contains(negativeLower)
                || contraindications.toLowerCase(Locale.ROOT).contains(negativeLower)
                || nameLower.contains(negativeLower)
                || genericLower.contains(negativeLower)) {
                return null;
            }
        }

        int score = 0;

        for (int i = 0; i < drugKeywords.size(); i++) {
            String keyword = drugKeywords.get(i);
            if (keyword.isBlank()) {
                continue;
            }
            int keywordScore = scoreDrugKeyword(keyword, nameLower, genericLower, searchable);
            if (keywordScore > 0 && i == 0) {
                keywordScore += 10;
            }
            score += keywordScore;
        }

        for (String keyword : genericKeywords) {
            if (keyword.isBlank()) {
                continue;
            }
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (genericLower.equals(lower)) {
                score += 35;
            } else if (genericLower.contains(lower)) {
                score += 25;
            }
        }

        for (String keyword : categoryKeywords) {
            if (keyword.isBlank() || isBroadCategory(keyword)) {
                continue;
            }
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (categoryLower.contains(lower)) {
                score += 18;
            }
        }

        for (String keyword : indicationKeywords) {
            if (keyword.isBlank()) {
                continue;
            }
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (instructionsLower.contains(lower)) {
                score += 20;
            } else if (nameLower.contains(lower)) {
                score += 10;
            } else if (searchable.contains(lower)) {
                score += 6;
            }
        }

        if (score <= 0) {
            return null;
        }

        if (stockQuantity(row) <= 0) {
            return null;
        }

        return new ScoredDrug(drugId, row, score);
    }

    private static int scoreDrugKeyword(
        String keyword,
        String nameLower,
        String genericLower,
        String searchable
    ) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        if (nameLower.equals(lower) || genericLower.equals(lower)) {
            return 50;
        }
        if (nameLower.contains(lower) || genericLower.contains(lower)) {
            return 40;
        }
        if (searchable.contains(lower)) {
            return 12;
        }
        return 0;
    }

    private static List<String> filterCategoryKeywords(List<String> categoryKeywords) {
        List<String> filtered = new ArrayList<>();
        for (String keyword : categoryKeywords) {
            if (!isBroadCategory(keyword)) {
                filtered.add(keyword);
            }
        }
        return filtered;
    }

    private static boolean isBroadCategory(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return BROAD_CATEGORY_KEYWORDS.contains(keyword.trim());
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

    private static int stockQuantity(Map<String, Object> row) {
        Object value = row.get("stockQuantity");
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
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
            candidate.put("stockQuantity", stockQuantity(row));
            candidate.put("unit", row.getOrDefault("unit", "盒"));
            candidate.put("lowStockThreshold", row.getOrDefault("lowStockThreshold", 20));
            candidate.put("matchScore", score);
            return candidate;
        }
    }
}
