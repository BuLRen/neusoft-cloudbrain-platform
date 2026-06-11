package com.xikang.medtech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.entity.CheckRequest;
import com.xikang.medtech.entity.InspectionRequest;
import com.xikang.medtech.entity.MedicalTechnology;
import com.xikang.medtech.entity.ResultFormCategory;
import com.xikang.medtech.entity.ResultFormField;
import com.xikang.medtech.mapper.CheckRequestMapper;
import com.xikang.medtech.mapper.InspectionRequestMapper;
import com.xikang.medtech.mapper.MedicalTechnologyMapper;
import com.xikang.medtech.mapper.ResultFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultFormService {

    private static final String OWNER_CATEGORY = "category";
    private static final String OWNER_TECH_EXTENSION = "tech_extension";
    private static final String DEFAULT_CATEGORY = "general_check";
    private static final String DEFAULT_LAB_CATEGORY = "general_lab";

    private final ResultFormMapper resultFormMapper;
    private final MedicalTechnologyMapper medicalTechnologyMapper;
    private final CheckRequestMapper checkRequestMapper;
    private final InspectionRequestMapper inspectionRequestMapper;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listCategories() {
        return resultFormMapper.selectAllCategories().stream().map(this::toCategoryMap).toList();
    }

    public List<Map<String, Object>> listCategoryFields(String categoryCode) {
        ensureCategoryExists(categoryCode);
        return resultFormMapper.selectFieldsByOwner(OWNER_CATEGORY, categoryCode).stream()
            .map(this::toFieldMap)
            .toList();
    }

    @Transactional
    public void saveCategoryFields(String categoryCode, List<Map<String, Object>> fields) {
        ensureCategoryExists(categoryCode);
        validateFieldPayload(fields, OWNER_CATEGORY, categoryCode, Set.of());
        resultFormMapper.deleteFieldsByOwner(OWNER_CATEGORY, categoryCode);
        insertFields(OWNER_CATEGORY, categoryCode, fields);
    }

    public Map<String, Object> getTechExtensionContext(Long techId) {
        MedicalTechnology tech = requireCheckTechnology(techId);
        String categoryCode = resolveCategoryCode(tech.getAiCategoryCode());
        List<Map<String, Object>> baseFields = resultFormMapper
            .selectFieldsByOwner(OWNER_CATEGORY, categoryCode)
            .stream()
            .map(this::toFieldMap)
            .toList();
        List<Map<String, Object>> extensionFields = resultFormMapper
            .selectFieldsByOwner(OWNER_TECH_EXTENSION, String.valueOf(techId))
            .stream()
            .map(this::toFieldMap)
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("techId", tech.getId());
        result.put("techCode", tech.getTechCode());
        result.put("techName", tech.getTechName());
        result.put("categoryCode", categoryCode);
        result.put("categoryName", categoryName(categoryCode));
        result.put("baseFields", baseFields);
        result.put("extensionFields", extensionFields);
        return result;
    }

    @Transactional
    public void saveTechExtensions(Long techId, List<Map<String, Object>> fields) {
        MedicalTechnology tech = requireCheckTechnology(techId);
        String categoryCode = resolveCategoryCode(tech.getAiCategoryCode());
        Set<String> baseKeys = resultFormMapper.selectFieldsByOwner(OWNER_CATEGORY, categoryCode).stream()
            .map(ResultFormField::getFieldKey)
            .collect(Collectors.toSet());
        validateFieldPayload(fields, OWNER_TECH_EXTENSION, String.valueOf(techId), baseKeys);
        resultFormMapper.deleteFieldsByOwner(OWNER_TECH_EXTENSION, String.valueOf(techId));
        insertFields(OWNER_TECH_EXTENSION, String.valueOf(techId), fields);
    }

    public Map<String, Object> resolveByCheckRequestId(Long checkRequestId) {
        CheckRequest request = checkRequestMapper.selectById(checkRequestId);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        Map<String, Object> schema = resolveByMedicalTechnologyId(request.getMedicalTechnologyId());
        schema.put("checkRequestId", checkRequestId);
        schema.put("existingValues", parseExistingValues(request.getCheckResult(), "check"));
        return schema;
    }

    public Map<String, Object> resolveByInspectionRequestId(Long inspectionRequestId) {
        InspectionRequest request = inspectionRequestMapper.selectById(inspectionRequestId);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        Map<String, Object> schema = resolveByMedicalTechnologyId(request.getMedicalTechnologyId());
        schema.put("inspectionRequestId", inspectionRequestId);
        schema.put("existingValues", parseExistingValues(request.getInspectionResult(), "inspection"));
        return schema;
    }

    public Map<String, Object> resolveByMedicalTechnologyId(Long medicalTechnologyId) {
        MedicalTechnology tech = requireMedtechTechnology(medicalTechnologyId);
        return buildResolvedSchema(tech);
    }

    public String buildResultPayload(Long medicalTechnologyId, Map<String, Object> resultData) {
        MedicalTechnology tech = requireMedtechTechnology(medicalTechnologyId);
        Map<String, Object> schema = buildResolvedSchema(tech);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
        Map<String, Object> values = extractValues(resultData, fields, tech.getTechType());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("categoryCode", schema.get("categoryCode"));
        payload.put("medicalTechnologyId", tech.getId());
        payload.put("techCode", tech.getTechCode());
        payload.put("techName", tech.getTechName());
        payload.put("submittedAt", LocalDateTime.now().toString());
        payload.put("values", values);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "结果序列化失败");
        }
    }

    public Map<String, Object> parseResultPayload(String checkResult) {
        if (checkResult == null || checkResult.isBlank()) {
            return null;
        }
        String trimmed = checkResult.trim();
        if (!trimmed.startsWith("{")) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("legacyText", trimmed);
            return legacy;
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("legacyText", trimmed);
            return legacy;
        }
    }

    public String buildResultSummary(String checkResult) {
        Map<String, Object> payload = parseResultPayload(checkResult);
        if (payload == null) {
            return null;
        }
        if (payload.containsKey("legacyText")) {
            String text = String.valueOf(payload.get("legacyText"));
            return text.length() > 80 ? text.substring(0, 80) + "…" : text;
        }
        Object valuesObj = payload.get("values");
        if (!(valuesObj instanceof Map<?, ?> values)) {
            return null;
        }
        for (Object value : values.values()) {
            if (value != null && !String.valueOf(value).isBlank()) {
                String text = String.valueOf(value).trim();
                return text.length() > 80 ? text.substring(0, 80) + "…" : text;
            }
        }
        return null;
    }

    private Map<String, Object> buildResolvedSchema(MedicalTechnology tech) {
        String categoryCode = resolveCategoryCode(tech);
        List<ResultFormField> baseFields = new ArrayList<>(
            resultFormMapper.selectFieldsByOwner(OWNER_CATEGORY, categoryCode)
        );
        if (baseFields.isEmpty()) {
            baseFields = defaultCategoryFields(categoryCode, tech.getTechType());
        }

        List<ResultFormField> extensionFields = resultFormMapper.selectFieldsByOwner(
            OWNER_TECH_EXTENSION, String.valueOf(tech.getId())
        );

        Set<String> baseKeys = baseFields.stream().map(ResultFormField::getFieldKey).collect(Collectors.toSet());
        for (ResultFormField extension : extensionFields) {
            if (baseKeys.contains(extension.getFieldKey())) {
                throw new BusinessException(500, "项目扩展字段与分类字段冲突: " + extension.getFieldKey());
            }
        }

        List<ResultFormField> merged = new ArrayList<>(baseFields);
        merged.addAll(extensionFields);
        merged.sort(Comparator.comparing(ResultFormField::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ResultFormField::getId, Comparator.nullsLast(Long::compareTo)));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("categoryCode", categoryCode);
        schema.put("categoryName", categoryName(categoryCode));
        schema.put("medicalTechnologyId", tech.getId());
        schema.put("techCode", tech.getTechCode());
        schema.put("techName", tech.getTechName());
        schema.put("fields", merged.stream().map(this::toFieldMap).toList());
        schema.put("baseFieldCount", baseFields.size());
        schema.put("extensionFieldCount", extensionFields.size());
        return schema;
    }

    private Map<String, Object> extractValues(Map<String, Object> resultData, List<Map<String, Object>> fields, String techType) {
        @SuppressWarnings("unchecked")
        Map<String, Object> inputValues = resultData.get("values") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : resultData;

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            String fieldKey = String.valueOf(field.get("fieldKey"));
            Object raw = inputValues.get(fieldKey);
            if (raw == null && ("checkResult".equals(fieldKey) || "result".equals(fieldKey))) {
                raw = firstNonBlank(
                    (String) resultData.get("checkResult"),
                    (String) resultData.get("result")
                );
            }
            if (raw == null && ("inspectionResult".equals(fieldKey) || "result".equals(fieldKey))) {
                raw = firstNonBlank(
                    (String) resultData.get("inspectionResult"),
                    (String) resultData.get("result")
                );
            }
            if (raw == null && "checkRemark".equals(fieldKey)) {
                raw = resultData.get("checkRemark");
            }
            if (raw == null && "inspectionRemark".equals(fieldKey)) {
                raw = resultData.get("inspectionRemark");
            }
            String fieldType = String.valueOf(field.get("fieldType"));
            boolean required = Boolean.TRUE.equals(field.get("required"));
            Object normalized = normalizeValue(raw, fieldType, required, String.valueOf(field.get("fieldLabel")));
            values.put(fieldKey, normalized);
        }

        if ("inspection".equals(techType) && !values.containsKey("inspectionResult")) {
            String legacy = firstNonBlank(
                (String) resultData.get("inspectionResult"),
                (String) resultData.get("result")
            );
            if (legacy != null) {
                values.put("inspectionResult", legacy);
            }
        }

        return values;
    }

    private Object normalizeValue(Object raw, String fieldType, boolean required, String label) {
        if (raw == null || (raw instanceof String text && text.isBlank())) {
            if (required) {
                throw new BusinessException(400, label + "不能为空");
            }
            return null;
        }
        if ("number".equals(fieldType)) {
            if (raw instanceof Number number) {
                return number;
            }
            try {
                return Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, label + "必须是数字");
            }
        }
        String text = String.valueOf(raw).trim();
        return text;
    }

    private void validateFieldPayload(
        List<Map<String, Object>> fields,
        String ownerType,
        String ownerKey,
        Set<String> forbiddenKeys
    ) {
        if (fields == null) {
            throw new BusinessException(400, "字段列表不能为空");
        }
        Set<String> keys = new HashSet<>();
        int order = 1;
        for (Map<String, Object> field : fields) {
            String fieldKey = trimRequired((String) field.get("fieldKey"), "字段标识");
            String fieldLabel = trimRequired((String) field.get("fieldLabel"), "字段标签");
            String fieldType = trimToNull((String) field.get("fieldType"));
            if (fieldType == null || !Set.of("text", "textarea", "number").contains(fieldType)) {
                throw new BusinessException(400, "字段类型无效: " + fieldKey);
            }
            if (!keys.add(fieldKey)) {
                throw new BusinessException(400, "字段标识重复: " + fieldKey);
            }
            if (forbiddenKeys.contains(fieldKey)) {
                throw new BusinessException(400, "扩展字段不得与分类基础字段重复: " + fieldKey);
            }
            field.put("fieldKey", fieldKey);
            field.put("fieldLabel", fieldLabel);
            field.put("fieldType", fieldType);
            field.put("required", Boolean.TRUE.equals(field.get("required")));
            field.put("sortOrder", field.get("sortOrder") == null ? order : toInt(field.get("sortOrder"), order));
            order++;
        }
    }

    private void insertFields(String ownerType, String ownerKey, List<Map<String, Object>> fields) {
        for (Map<String, Object> field : fields) {
            ResultFormField row = new ResultFormField();
            row.setOwnerType(ownerType);
            row.setOwnerKey(ownerKey);
            row.setFieldKey((String) field.get("fieldKey"));
            row.setFieldLabel((String) field.get("fieldLabel"));
            row.setFieldType((String) field.get("fieldType"));
            row.setRequired(Boolean.TRUE.equals(field.get("required")));
            row.setSortOrder(toInt(field.get("sortOrder"), 0));
            row.setPlaceholder(trimToNull((String) field.get("placeholder")));
            row.setMaxLength(field.get("maxLength") == null ? null : toInt(field.get("maxLength"), null));
            row.setOptionsJson(trimToNull((String) field.get("optionsJson")));
            resultFormMapper.insertField(row);
        }
    }

    private MedicalTechnology requireCheckTechnology(Long techId) {
        MedicalTechnology tech = requireMedtechTechnology(techId);
        if (!"check".equals(tech.getTechType())) {
            throw new BusinessException(400, "仅支持检查类项目配置结果表单");
        }
        return tech;
    }

    private MedicalTechnology requireMedtechTechnology(Long techId) {
        if (techId == null) {
            throw new BusinessException(400, "医技项目 ID 不能为空");
        }
        MedicalTechnology tech = medicalTechnologyMapper.selectById(techId);
        if (tech == null) {
            throw new BusinessException(404, "医技项目不存在");
        }
        String techType = tech.getTechType();
        if (!"check".equals(techType) && !"inspection".equals(techType)) {
            throw new BusinessException(400, "当前项目类型不支持动态结果表单");
        }
        return tech;
    }

    private void ensureCategoryExists(String categoryCode) {
        if (resultFormMapper.selectCategoryByCode(categoryCode) == null) {
            throw new BusinessException(404, "表单分类不存在");
        }
    }

    private String resolveCategoryCode(MedicalTechnology tech) {
        String aiCategoryCode = tech.getAiCategoryCode();
        if (aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct")) {
            return "imaging_ct";
        }
        if ("inspection".equals(tech.getTechType())
            || "general_lab".equals(aiCategoryCode)) {
            return DEFAULT_LAB_CATEGORY;
        }
        return DEFAULT_CATEGORY;
    }

    private String resolveCategoryCode(String aiCategoryCode) {
        if (aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct")) {
            return "imaging_ct";
        }
        if ("general_lab".equals(aiCategoryCode)) {
            return DEFAULT_LAB_CATEGORY;
        }
        return DEFAULT_CATEGORY;
    }

    private String categoryName(String categoryCode) {
        ResultFormCategory category = resultFormMapper.selectCategoryByCode(categoryCode);
        return category == null ? categoryCode : category.getCategoryName();
    }

    private List<ResultFormField> defaultCategoryFields(String categoryCode, String techType) {
        ResultFormField primary = new ResultFormField();
        primary.setOwnerType(OWNER_CATEGORY);
        primary.setOwnerKey(categoryCode);
        primary.setFieldType("textarea");
        primary.setRequired(true);
        primary.setSortOrder(1);

        if ("inspection".equals(techType) || DEFAULT_LAB_CATEGORY.equals(categoryCode)) {
            primary.setFieldKey("inspectionResult");
            primary.setFieldLabel("检验结果");
        } else {
            primary.setFieldKey("checkResult");
            primary.setFieldLabel("检查结果");
        }
        return List.of(primary);
    }

    private Map<String, Object> parseExistingValues(String storedResult, String techType) {
        Map<String, Object> payload = parseResultPayload(storedResult);
        if (payload == null) {
            return Map.of();
        }
        if (payload.get("values") instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (payload.containsKey("legacyText")) {
            String legacyKey = "inspection".equals(techType) ? "inspectionResult" : "checkResult";
            return Map.of(legacyKey, payload.get("legacyText"));
        }
        return Map.of();
    }

    private Map<String, Object> toCategoryMap(ResultFormCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryCode", category.getCategoryCode());
        map.put("categoryName", category.getCategoryName());
        map.put("description", category.getDescription());
        return map;
    }

    private Map<String, Object> toFieldMap(ResultFormField field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", field.getId());
        map.put("fieldKey", field.getFieldKey());
        map.put("fieldLabel", field.getFieldLabel());
        map.put("fieldType", field.getFieldType());
        map.put("required", Boolean.TRUE.equals(field.getRequired()));
        map.put("sortOrder", field.getSortOrder());
        map.put("placeholder", field.getPlaceholder());
        map.put("maxLength", field.getMaxLength());
        map.put("optionsJson", field.getOptionsJson());
        return map;
    }

    private static String trimRequired(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(400, label + "不能为空");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer toInt(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
