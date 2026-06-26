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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiseaseAiSearchService {

    private static final Pattern ICD_PREFIX = Pattern.compile("^[A-Z][0-9]{2}");

    private final PhysicianMapper physicianMapper;

    public DiseaseAiSearchService(PhysicianMapper physicianMapper) {
        this.physicianMapper = physicianMapper;
    }

    public List<Map<String, Object>> search(DiseaseAiSearchRequest request) {
        int limit = normalizeLimit(request.getLimit());
        List<String> diseaseKeywords = mergeKeywords(request.getDiseaseKeywords(), request.getSymptomKeywords());
        List<String> icdPrefixes = normalizeIcdPrefixes(request.getIcdPrefixes());
        List<String> categoryKeywords = cleanKeywords(request.getCategoryKeywords());
        List<String> negativeKeywords = cleanKeywords(request.getNegativeKeywords());

        if (diseaseKeywords.isEmpty() && icdPrefixes.isEmpty() && categoryKeywords.isEmpty()) {
            return List.of();
        }

        int fetchLimit = Math.min(Math.max(limit * 3, limit), 200);
        List<Map<String, Object>> rawRows = physicianMapper.searchDiseasesForAi(
            diseaseKeywords,
            icdPrefixes,
            categoryKeywords,
            fetchLimit
        );

        List<ScoredDisease> scored = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            ScoredDisease item = scoreRow(row, diseaseKeywords, icdPrefixes, categoryKeywords, negativeKeywords);
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

    private ScoredDisease scoreRow(
        Map<String, Object> row,
        List<String> diseaseKeywords,
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
        String searchable = (diseaseName + " " + diseaseCode + " " + diseaseIcd + " " + diseaseCategory).toLowerCase(Locale.ROOT);

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

        for (String keyword : diseaseKeywords) {
            if (keyword.isBlank()) {
                continue;
            }
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (diseaseName.toLowerCase(Locale.ROOT).contains(lower)) {
                score += 35;
            } else if (diseaseCategory.toLowerCase(Locale.ROOT).contains(lower)) {
                score += 15;
            } else if (searchable.contains(lower)) {
                score += 10;
            }
        }

        for (String category : categoryKeywords) {
            if (!category.isBlank() && diseaseCategory.toLowerCase(Locale.ROOT).contains(category.toLowerCase(Locale.ROOT))) {
                score += 20;
            }
        }

        if (score <= 0) {
            score = 1;
        }

        return new ScoredDisease(id, diagnosisCode, diseaseName, diseaseIcd, diseaseCategory, score);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 80;
        }
        return Math.min(limit, 80);
    }

    private List<String> mergeKeywords(List<String> diseaseKeywords, List<String> symptomKeywords) {
        List<String> merged = new ArrayList<>();
        merged.addAll(cleanKeywords(diseaseKeywords));
        for (String keyword : cleanKeywords(symptomKeywords)) {
            if (!merged.contains(keyword)) {
                merged.add(keyword);
            }
        }
        return merged;
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

    private String str(Object value) {
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
