package com.xikang.physician.service;

import com.xikang.physician.dto.DiseaseAiSearchRequest;
import com.xikang.physician.mapper.PhysicianMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiseaseAiSearchService {

    private static final Pattern ICD_PREFIX = Pattern.compile("^[A-Z][0-9]{2}");

    /** Short/generic symptoms that must not match disease names (e.g. 头痛 → 偏头痛). */
    private static final Set<String> AMBIGUOUS_SYMPTOM_KEYWORDS = Set.of(
        "头痛", "发热", "咳嗽", "乏力", "疼痛", "恶心", "呕吐", "晕", "痒", "红", "肿"
    );

    private static final int PER_DISEASE_KEYWORD_LIMIT = 40;
    private static final int PER_ICD_PREFIX_LIMIT = 50;
    private static final int PER_CATEGORY_KEYWORD_LIMIT = 30;

    private final PhysicianMapper physicianMapper;

    public DiseaseAiSearchService(PhysicianMapper physicianMapper) {
        this.physicianMapper = physicianMapper;
    }

    public List<Map<String, Object>> search(DiseaseAiSearchRequest request) {
        int limit = normalizeLimit(request.getLimit());
        List<String> primaryKeywords = cleanKeywords(request.getDiseaseKeywords());
        List<String> symptomKeywords = cleanKeywords(request.getSymptomKeywords());
        List<String> icdPrefixes = normalizeIcdPrefixes(request.getIcdPrefixes());
        List<String> categoryKeywords = cleanKeywords(request.getCategoryKeywords());
        List<String> negativeKeywords = cleanKeywords(request.getNegativeKeywords());

        if (primaryKeywords.isEmpty() && icdPrefixes.isEmpty() && categoryKeywords.isEmpty()
            && symptomKeywords.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Object>> rawById = new LinkedHashMap<>();
        collectRows(rawById, fetchByBuckets(primaryKeywords, icdPrefixes, categoryKeywords, symptomKeywords));

        if (rawById.size() < Math.min(limit, 20)) {
            int fetchLimit = Math.min(Math.max(limit * 3, limit), 500);
            collectRows(rawById, physicianMapper.searchDiseasesForAi(
                primaryKeywords,
                icdPrefixes,
                categoryKeywords,
                fetchLimit
            ));
        }

        List<ScoredDisease> scored = new ArrayList<>();
        for (Map<String, Object> row : rawById.values()) {
            ScoredDisease item = scoreRow(
                row,
                primaryKeywords,
                symptomKeywords,
                icdPrefixes,
                categoryKeywords,
                negativeKeywords
            );
            if (item != null) {
                scored.add(item);
            }
        }

        scored.sort(Comparator.comparingInt(ScoredDisease::score).reversed());

        Map<Long, ScoredDisease> deduped = new LinkedHashMap<>();
        for (ScoredDisease item : scored) {
            deduped.putIfAbsent(item.id(), item);
            if (deduped.size() >= limit) {
                break;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoredDisease item : deduped.values()) {
            result.add(item.toResponseMap());
        }
        return result;
    }

    private List<Map<String, Object>> fetchByBuckets(
        List<String> primaryKeywords,
        List<String> icdPrefixes,
        List<String> categoryKeywords,
        List<String> symptomKeywords
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String keyword : primaryKeywords) {
            rows.addAll(physicianMapper.searchDiseasesByNameKeyword(keyword, PER_DISEASE_KEYWORD_LIMIT));
        }

        for (String prefix : icdPrefixes) {
            rows.addAll(physicianMapper.searchDiseasesByIcdPrefix(prefix, PER_ICD_PREFIX_LIMIT));
        }

        for (String category : categoryKeywords) {
            rows.addAll(physicianMapper.searchDiseasesByCategoryKeyword(category, PER_CATEGORY_KEYWORD_LIMIT));
        }

        for (String symptom : symptomKeywords) {
            if (isAmbiguousSymptom(symptom)) {
                continue;
            }
            if (symptom.length() >= 4) {
                rows.addAll(physicianMapper.searchDiseasesByNameKeyword(symptom, 15));
            }
            rows.addAll(physicianMapper.searchDiseasesByCategoryKeyword(symptom, 10));
        }

        return rows;
    }

    private static void collectRows(Map<Long, Map<String, Object>> target, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id != null) {
                target.putIfAbsent(id, row);
            }
        }
    }

    private ScoredDisease scoreRow(
        Map<String, Object> row,
        List<String> primaryKeywords,
        List<String> symptomKeywords,
        List<String> icdPrefixes,
        List<String> categoryKeywords,
        List<String> negativeKeywords
    ) {
        Long id = toLong(row.get("id"));
        if (id == null) {
            return null;
        }

        String diseaseCode = str(row.get("diseaseCode"));
        String diseaseName = str(row.get("diseaseName"));
        String diseaseIcd = str(row.get("diseaseIcd"));
        String diseaseCategory = str(row.get("diseaseCategory"));

        String diagnosisCode = !diseaseIcd.isBlank() ? diseaseIcd : diseaseCode;
        String searchable = (diseaseName + " " + diseaseCode + " " + diseaseIcd + " " + diseaseCategory)
            .toLowerCase(Locale.ROOT);
        String nameLower = diseaseName.toLowerCase(Locale.ROOT);
        String categoryLower = diseaseCategory.toLowerCase(Locale.ROOT);

        for (String negative : negativeKeywords) {
            if (!negative.isBlank() && searchable.contains(negative.toLowerCase(Locale.ROOT))) {
                return null;
            }
        }

        int score = 0;
        String icdUpper = diseaseIcd.toUpperCase(Locale.ROOT);

        for (String prefix : icdPrefixes) {
            if (!prefix.isBlank() && icdUpper.startsWith(prefix.toUpperCase(Locale.ROOT))) {
                score += 45;
            }
        }

        for (int i = 0; i < primaryKeywords.size(); i++) {
            String keyword = primaryKeywords.get(i);
            if (keyword.isBlank()) {
                continue;
            }
            int keywordScore = scorePrimaryKeyword(keyword, nameLower, categoryLower, searchable);
            if (keywordScore > 0 && i == 0) {
                keywordScore += 15;
            }
            score += keywordScore;
        }

        for (String keyword : symptomKeywords) {
            if (keyword.isBlank() || isAmbiguousSymptom(keyword)) {
                continue;
            }
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (categoryLower.contains(lower)) {
                score += 10;
            } else if (keyword.length() >= 4 && nameLower.contains(lower)) {
                score += 8;
            }
        }

        for (String category : categoryKeywords) {
            if (!category.isBlank() && categoryLower.contains(category.toLowerCase(Locale.ROOT))) {
                score += 20;
            }
        }

        if (score <= 0) {
            score = 1;
        }

        return new ScoredDisease(id, diagnosisCode, diseaseName, diseaseIcd, diseaseCategory, score);
    }

    private static int scorePrimaryKeyword(
        String keyword,
        String nameLower,
        String categoryLower,
        String searchable
    ) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        if (nameLower.equals(lower)) {
            return 50;
        }
        if (nameLower.contains(lower)) {
            return 40;
        }
        if (lower.contains(nameLower) && nameLower.length() >= 4) {
            return 30;
        }
        if (categoryLower.contains(lower)) {
            return 15;
        }
        if (searchable.contains(lower)) {
            return 10;
        }
        return 0;
    }

    private static boolean isAmbiguousSymptom(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String trimmed = keyword.trim();
        if (AMBIGUOUS_SYMPTOM_KEYWORDS.contains(trimmed)) {
            return true;
        }
        return trimmed.length() < 3;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 80;
        }
        return Math.min(limit, 80);
    }

    private List<String> cleanKeywords(List<String> keywords) {
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

    private List<String> normalizeIcdPrefixes(List<String> prefixes) {
        List<String> normalized = new ArrayList<>();
        if (prefixes == null) {
            return normalized;
        }
        for (String prefix : prefixes) {
            if (prefix == null) {
                continue;
            }
            Matcher matcher = ICD_PREFIX.matcher(prefix.trim().toUpperCase(Locale.ROOT));
            if (matcher.find()) {
                String value = matcher.group();
                if (!normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
    }

    private static Long toLong(Object value) {
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

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ScoredDisease(
        Long id,
        String diagnosisCode,
        String diagnosisName,
        String diseaseIcd,
        String diseaseCategory,
        int score
    ) {
        Map<String, Object> toResponseMap() {
            String icdPrefix = extractIcdPrefix(diseaseIcd);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("diagnosisCode", diagnosisCode);
            map.put("diagnosisName", diagnosisName);
            map.put("diseaseCode", diagnosisCode);
            map.put("diseaseName", diagnosisName);
            map.put("diseaseIcd", diseaseIcd);
            map.put("categoryCode", icdPrefix);
            map.put("categoryName", diseaseCategory);
            map.put("subcategoryCode", "");
            map.put("subcategoryName", "");
            map.put("chapterName", diseaseCategory);
            map.put("sectionName", "");
            map.put("matchScore", score);
            return map;
        }

        private String extractIcdPrefix(String icd) {
            if (icd == null || icd.isBlank()) {
                return "";
            }
            Matcher matcher = ICD_PREFIX.matcher(icd.toUpperCase(Locale.ROOT));
            return matcher.find() ? matcher.group() : "";
        }
    }
}
