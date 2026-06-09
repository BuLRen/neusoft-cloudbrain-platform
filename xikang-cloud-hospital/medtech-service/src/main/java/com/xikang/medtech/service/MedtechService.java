package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.entity.*;
import com.xikang.medtech.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MedTech Service - 医技服务（检查/检验/处置）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedtechService {

    private final CheckRequestMapper checkRequestMapper;
    private final InspectionRequestMapper inspectionRequestMapper;
    private final DisposalRequestMapper disposalRequestMapper;
    private final MedicalTechnologyMapper medicalTechnologyMapper;
    private final ResultFormService resultFormService;
    private final ObjectMapper objectMapper;

    // ==================== 检查相关 ====================

    /**
     * 获取待检查患者列表
     */
    public List<Map<String, Object>> getCheckApplications(Long registrationId, String checkState) {
        List<CheckRequest> requests;
        if (registrationId != null) {
            requests = checkRequestMapper.selectByRegisterId(registrationId);
        } else if (checkState != null && !checkState.isBlank()) {
            requests = checkRequestMapper.selectByCheckState(checkState.trim());
        } else {
            requests = checkRequestMapper.selectPending();
        }
        return requests.stream().map(this::toCheckMap).toList();
    }

    /**
     * 开始检查
     */
    @Transactional
    public void startCheck(Long id, Map<String, Object> operatorInfo) {
        log.info("开始检查 | checkRequestId={}", id);
        CheckRequest request = checkRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!"待检查".equals(request.getCheckState())) {
            throw new BusinessException(400, "当前状态不允许开始检查");
        }
        Long checkEmployeeId = extractLong(operatorInfo, "checkEmployeeId");
        if (checkEmployeeId != null) {
            checkRequestMapper.updateCheckStateWithEmployee(id, "检查中", checkEmployeeId);
        } else {
            checkRequestMapper.updateCheckState(id, "检查中");
        }
    }

    /**
     * 提交检查结果
     */
    @Transactional
    public Map<String, Object> submitCheckResult(Long id, Map<String, Object> resultData) {
        log.info("提交检查结果 | checkRequestId={}", id);

        CheckRequest request = checkRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!"检查中".equals(request.getCheckState())) {
            throw new BusinessException(400, "当前状态不允许录入结果");
        }

        String checkResult = resultFormService.buildResultPayload(request.getMedicalTechnologyId(), resultData);

        request.setCheckResult(checkResult);
        request.setCheckState("已完成");
        request.setCheckTime(LocalDateTime.now());
        request.setCheckRemark(trimToNull((String) resultData.get("checkRemark")));
        request.setInputcheckEmployeeId(extractLong(resultData, "inputcheckEmployeeId"));
        checkRequestMapper.updateResult(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("checkTime", request.getCheckTime());
        response.put("aiAnalysisTriggered", resultData.get("aiAnalysis") != null);

        return response;
    }

    /**
     * 获取检查报告
     */
    public Map<String, Object> getCheckReport(Long id) {
        CheckRequest request = checkRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        return toCheckDetailMap(request);
    }

    // ==================== 检验相关 ====================

    /**
     * 获取待检验患者列表
     */
    public List<Map<String, Object>> getInspectionApplications(Long registrationId, Integer status) {
        List<InspectionRequest> requests;
        if (registrationId != null) {
            requests = inspectionRequestMapper.selectByRegisterId(registrationId);
        } else if (status != null) {
            requests = inspectionRequestMapper.selectByStatus(status);
        } else {
            requests = inspectionRequestMapper.selectPending();
        }
        return requests.stream().map(this::toInspectionMap).toList();
    }

    /**
     * 开始检验
     */
    @Transactional
    public void startInspection(Long id) {
        log.info("开始检验 | inspectionRequestId={}", id);
        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        if (request.getStatus() != 1) {
            throw new BusinessException(400, "当前状态不允许开始检验");
        }
        inspectionRequestMapper.updateStatus(id, 2); // 执行中
    }

    /**
     * 记录采样
     */
    @Transactional
    public void recordSpecimen(Long id, Map<String, Object> specimenInfo) {
        log.info("记录采样 | inspectionRequestId={}", id);
        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        inspectionRequestMapper.updateSpecimenTime(id, LocalDateTime.now());
    }

    /**
     * 提交检验结果
     */
    @Transactional
    public Map<String, Object> submitInspectionResult(Long id, Map<String, Object> resultData) {
        log.info("提交检验结果 | inspectionRequestId={}", id);

        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }

        String result;
        try {
            result = objectMapper.writeValueAsString(resultData.get("result"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("检验结果序列化失败", e);
            result = "{}";
        }
        String aiAnalysis = (String) resultData.get("aiAnalysis");

        request.setResult(result);
        request.setAiAnalysis(aiAnalysis);
        request.setStatus(3); // 已完成
        request.setResultTime(LocalDateTime.now());
        inspectionRequestMapper.update(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("resultTime", request.getResultTime());
        response.put("aiAnalysisTriggered", aiAnalysis != null);

        return response;
    }

    // ==================== 处置相关 ====================

    /**
     * 获取待处置患者列表
     */
    public List<Map<String, Object>> getDisposalApplications(Long registrationId, Integer status) {
        List<DisposalRequest> requests;
        if (registrationId != null) {
            requests = disposalRequestMapper.selectByRegisterId(registrationId);
        } else if (status != null) {
            requests = disposalRequestMapper.selectByStatus(status);
        } else {
            requests = disposalRequestMapper.selectPending();
        }
        return requests.stream().map(this::toDisposalMap).toList();
    }

    /**
     * 开始处置
     */
    @Transactional
    public void startDisposal(Long id) {
        log.info("开始处置 | disposalRequestId={}", id);
        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        if (request.getStatus() != 1) {
            throw new BusinessException(400, "当前状态不允许开始处置");
        }
        disposalRequestMapper.updateStatus(id, 2); // 执行中
    }

    /**
     * 提交处置结果
     */
    @Transactional
    public void submitDisposalResult(Long id, Map<String, Object> resultData) {
        log.info("提交处置结果 | disposalRequestId={}", id);

        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }

        String result = (String) resultData.get("result");
        String remarks = (String) resultData.get("remarks");

        request.setResult(result);
        request.setRemarks(remarks);
        request.setStatus(3); // 已完成
        request.setExecuteTime(LocalDateTime.now());
        disposalRequestMapper.update(request);
    }

    // ==================== 基础数据 ====================

    private static final Set<String> ALLOWED_TECH_TYPES = Set.of("check", "inspection", "disposal");

    /**
     * 获取医技项目列表
     */
    public List<Map<String, Object>> listDepartments() {
        return medicalTechnologyMapper.selectDepartmentOptions();
    }

    public Map<String, Object> pageMedicalTechnologies(String techType, String keyword, Integer page, Integer size) {
        String normalizedType = normalizeTechType(techType);
        String normalizedKeyword = normalizeKeyword(keyword);
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        int offset = (currentPage - 1) * pageSize;

        List<MedicalTechnology> records = medicalTechnologyMapper.selectList(
            normalizedType, normalizedKeyword, offset, pageSize
        );
        long total = medicalTechnologyMapper.countList(normalizedType, normalizedKeyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", total == 0 ? 0 : (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    /**
     * 获取医技项目详情
     */
    public MedicalTechnology getMedicalTechnology(Long id) {
        MedicalTechnology technology = medicalTechnologyMapper.selectById(id);
        if (technology == null) {
            throw new BusinessException(404, "医技项目不存在");
        }
        return technology;
    }

    @Transactional
    public MedicalTechnology createMedicalTechnology(MedicalTechnology input) {
        validateMedicalTechnology(input, null, true);
        if (medicalTechnologyMapper.selectByTechCode(input.getTechCode(), null) != null) {
            throw new BusinessException(400, "项目编码已存在");
        }
        medicalTechnologyMapper.insert(input);
        return getMedicalTechnology(input.getId());
    }

    @Transactional
    public MedicalTechnology updateMedicalTechnology(Long id, MedicalTechnology input) {
        MedicalTechnology existing = getMedicalTechnology(id);
        validateMedicalTechnology(input, existing, false);
        if (medicalTechnologyMapper.selectByTechCode(input.getTechCode(), id) != null) {
            throw new BusinessException(400, "项目编码已存在");
        }
        int refs = medicalTechnologyMapper.countReferences(id);
        if (refs > 0 && !existing.getTechType().equals(input.getTechType())) {
            throw new BusinessException(400, "该项目已被申请单引用，不能修改项目类型");
        }
        input.setId(id);
        medicalTechnologyMapper.update(input);
        return getMedicalTechnology(id);
    }

    @Transactional
    public void deleteMedicalTechnology(Long id) {
        getMedicalTechnology(id);
        int refs = medicalTechnologyMapper.countReferences(id);
        if (refs > 0) {
            throw new BusinessException(400, "该项目已被申请单引用，无法删除");
        }
        medicalTechnologyMapper.deleteById(id);
    }

    private void validateMedicalTechnology(MedicalTechnology input, MedicalTechnology existing, boolean creating) {
        if (input == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String techCode = trimToNull(input.getTechCode());
        String techName = trimToNull(input.getTechName());
        if (creating && (techCode == null || techCode.isEmpty())) {
            throw new BusinessException(400, "项目编码不能为空");
        }
        if (techName == null || techName.isEmpty()) {
            throw new BusinessException(400, "项目名称不能为空");
        }
        String techType = trimToNull(input.getTechType());
        if (techType == null || !ALLOWED_TECH_TYPES.contains(techType)) {
            throw new BusinessException(400, "项目类型无效，应为 check / inspection / disposal");
        }
        BigDecimal price = input.getTechPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "单价不能为负数");
        }
        if (creating) {
            input.setTechCode(techCode);
        } else if (existing != null) {
            input.setTechCode(existing.getTechCode());
        }
        input.setTechName(techName);
        input.setTechType(techType);
        input.setTechFormat(trimToNull(input.getTechFormat()));
        input.setPriceType(trimToNull(input.getPriceType()));
        input.setAiCategoryCode(trimToNull(input.getAiCategoryCode()));
    }

    private static String normalizeTechType(String techType) {
        if (techType == null) {
            return null;
        }
        String trimmed = techType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ==================== 转换方法 ====================

    private Map<String, Object> toCheckMap(CheckRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("caseNumber", request.getCaseNumber());
        map.put("patientName", request.getPatientName());
        map.put("techName", request.getTechName());
        map.put("position", request.getCheckPosition());
        map.put("info", request.getCheckInfo());
        map.put("statusText", request.getCheckState());
        map.put("checkState", request.getCheckState());
        map.put("checkTime", request.getCheckTime());
        map.put("creationTime", request.getCreationTime());
        return map;
    }

    private Map<String, Object> toCheckDetailMap(CheckRequest request) {
        Map<String, Object> map = toCheckMap(request);
        map.put("medicalTechnologyId", request.getMedicalTechnologyId());
        map.put("checkResult", request.getCheckResult());
        map.put("result", request.getCheckResult());
        map.put("checkRemark", request.getCheckRemark());
        map.put("resultPayload", resultFormService.parseResultPayload(request.getCheckResult()));
        map.put("resultSummary", resultFormService.buildResultSummary(request.getCheckResult()));
        return map;
    }

    private Map<String, Object> toInspectionMap(InspectionRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("patientId", request.getPatientId());
        map.put("patientName", request.getPatientName());
        map.put("physicianName", request.getPhysicianName());
        map.put("medicalTechnologyName", request.getMedicalTechnologyName());
        map.put("specimenType", request.getSpecimenType());
        map.put("status", request.getStatus());
        map.put("statusName", getInspectionStatusName(request.getStatus()));
        map.put("specimenTime", request.getSpecimenTime());
        map.put("resultTime", request.getResultTime());
        map.put("createTime", request.getCreateTime());
        return map;
    }

    private Map<String, Object> toDisposalMap(DisposalRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("patientId", request.getPatientId());
        map.put("patientName", request.getPatientName());
        map.put("physicianName", request.getPhysicianName());
        map.put("medicalTechnologyName", request.getMedicalTechnologyName());
        map.put("description", request.getDescription());
        map.put("quantity", request.getQuantity());
        map.put("status", request.getStatus());
        map.put("statusName", getDisposalStatusName(request.getStatus()));
        map.put("executeTime", request.getExecuteTime());
        map.put("createTime", request.getCreateTime());
        return map;
    }

    private String getInspectionStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待缴费";
            case 1 -> "待执行";
            case 2 -> "执行中";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private String getDisposalStatusName(Integer status) {
        return getInspectionStatusName(status);
    }

    private static Long extractLong(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
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
