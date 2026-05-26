package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.entity.*;
import com.xikang.medtech.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    // ==================== 检查相关 ====================

    /**
     * 获取待检查患者列表
     */
    public List<Map<String, Object>> getCheckApplications(Long registrationId, Integer status) {
        List<CheckRequest> requests;
        if (registrationId != null) {
            requests = checkRequestMapper.selectByRegisterId(registrationId);
        } else if (status != null) {
            requests = checkRequestMapper.selectByStatus(status);
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
        if (request.getStatus() != 1) {
            throw new BusinessException(400, "当前状态不允许开始检查");
        }
        checkRequestMapper.updateStatus(id, 2); // 执行中
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

        String result = (String) resultData.get("result");
        String aiAnalysis = (String) resultData.get("aiAnalysis");
        String findings = (String) resultData.get("findings");
        String conclusion = (String) resultData.get("conclusion");
        String impression = (String) resultData.get("impression");

        // 更新检查结果
        request.setResult(result);
        request.setFindings(findings);
        request.setConclusion(conclusion);
        request.setImpression(impression);
        request.setAiAnalysis(aiAnalysis);
        request.setStatus(3); // 已完成
        request.setReportTime(LocalDateTime.now());
        checkRequestMapper.update(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("reportTime", request.getReportTime());
        response.put("aiAnalysisTriggered", aiAnalysis != null);

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

    /**
     * 获取医技项目列表
     */
    public List<MedicalTechnology> getMedicalTechnologies(String type) {
        if (type != null) {
            return medicalTechnologyMapper.selectByType(type);
        }
        return medicalTechnologyMapper.selectAll();
    }

    /**
     * 获取医技项目详情
     */
    public MedicalTechnology getMedicalTechnology(Long id) {
        return medicalTechnologyMapper.selectById(id);
    }

    // ==================== 转换方法 ====================

    private Map<String, Object> toCheckMap(CheckRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("patientId", request.getPatientId());
        map.put("patientName", request.getPatientName());
        map.put("physicianName", request.getPhysicianName());
        map.put("medicalTechnologyName", request.getMedicalTechnologyName());
        map.put("clinicalDiagnosis", request.getClinicalDiagnosis());
        map.put("bodyPart", request.getBodyPart());
        map.put("status", request.getStatus());
        map.put("statusName", getCheckStatusName(request.getStatus()));
        map.put("checkTime", request.getCheckTime());
        map.put("reportTime", request.getReportTime());
        map.put("createTime", request.getCreateTime());
        return map;
    }

    private Map<String, Object> toCheckDetailMap(CheckRequest request) {
        Map<String, Object> map = toCheckMap(request);
        map.put("result", request.getResult());
        map.put("findings", request.getFindings());
        map.put("conclusion", request.getConclusion());
        map.put("impression", request.getImpression());
        map.put("aiAnalysis", request.getAiAnalysis());
        map.put("reportUrl", request.getReportUrl());
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

    private String getCheckStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待缴费";
            case 1 -> "待执行";
            case 2 -> "执行中";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private String getInspectionStatusName(Integer status) {
        return getCheckStatusName(status);
    }

    private String getDisposalStatusName(Integer status) {
        return getCheckStatusName(status);
    }
}
