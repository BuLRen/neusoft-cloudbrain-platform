package com.xikang.medtech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.client.CtViewerClient;
import com.xikang.medtech.client.PaymentClient;
import com.xikang.medtech.client.PhysicianW3Client;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.entity.*;
import com.xikang.medtech.mapper.*;
import com.xikang.medtech.ai.CriticalValueDetector;
import com.xikang.medtech.critical.CriticalDetectResult;
import com.xikang.medtech.util.CtCategoryResolver;
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
    private final ResultFormMapper resultFormMapper;
    private final MedtechStatsMapper medtechStatsMapper;
    private final ResultFormService resultFormService;
    private final ObjectMapper objectMapper;
    private final PhysicianW3Client physicianW3Client;
    private final PaymentClient paymentClient;
    private final CtViewerClient ctViewerClient;
    private final CriticalValueDetector criticalValueDetector;
    private final CriticalValueService criticalValueService;

    // ==================== 检查相关 ====================

    /**
     * 获取待检查患者列表
     */
    public List<Map<String, Object>> getCheckApplications(Long registrationId, String checkState) {
        Long departmentId = departmentIdFilter();
        List<CheckRequest> requests;
        if (registrationId != null) {
            requests = checkRequestMapper.selectByRegisterId(registrationId, departmentId);
        } else if (checkState != null && !checkState.isBlank()) {
            requests = checkRequestMapper.selectByCheckState(checkState.trim(), departmentId);
        } else {
            requests = checkRequestMapper.selectPending(departmentId);
        }
        return enrichWithPayment(requests.stream().map(this::toCheckMap).toList(), "CHECK_FEE");
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
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        paymentClient.assertItemPaid(request.getRegisterId(), "CHECK_FEE", id, "检查费");
        Long checkEmployeeId = resolveOperatorEmployeeId(operatorInfo, "checkEmployeeId");
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
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());

        if (CtCategoryResolver.isCt(request.getAiCategoryCode())) {
            String volumeId = trimToNull(request.getImagingVolumeId());
            if (volumeId == null) {
                throw new BusinessException(400, "CT 检查须先上传并绑定影像后才能提交诊断报告");
            }
        }

        String checkResult = resultFormService.buildResultPayload(request.getMedicalTechnologyId(), resultData);

        request.setCheckResult(checkResult);
        request.setCheckState("已完成");
        request.setCheckTime(LocalDateTime.now());
        request.setCheckRemark(trimToNull((String) resultData.get("checkRemark")));
        request.setInputcheckEmployeeId(resolveOperatorEmployeeId(resultData, "inputcheckEmployeeId"));
        checkRequestMapper.updateResult(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("checkTime", request.getCheckTime());
        response.put("aiAnalysisTriggered", resultData.get("aiAnalysis") != null);
        physicianW3Client.triggerW3Async(request.getRegisterId());
        attachCriticalDetect(response, request.getTechCode(), request.getTechName(), resultData);

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
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        Map<String, Object> detail = toCheckDetailMap(request);
        enrichSingleWithPayment(detail, "CHECK_FEE");
        return detail;
    }

    /**
     * 归档检查申请
     */
    @Transactional
    public void archiveCheck(Long id, Map<String, Object> archiveData) {
        log.info("归档检查 | checkRequestId={}", id);
        CheckRequest request = checkRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!isArchivableCheckState(request.getCheckState())) {
            throw new BusinessException(400, "当前状态不允许归档");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        String remark = buildArchiveRemark(archiveData);
        checkRequestMapper.updateArchive(id, "已归档", remark);
    }

    /**
     * 获取检查单绑定的 CT 影像信息
     */
    public Map<String, Object> getCheckImaging(Long id) {
        CheckRequest request = checkRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        return buildImagingMap(request);
    }

    /**
     * 执行 CT 伪影分析并持久化到检查单
     */
    @Transactional
    public Map<String, Object> analyzeCheckImaging(Long id) {
        CheckRequest request = requireCtImagingContext(id, true);
        String volumeId = request.getImagingVolumeId();
        Map<String, Object> analysis = ctViewerClient.analyzeVolume(volumeId);
        LocalDateTime analyzedAt = LocalDateTime.now();
        try {
            String json = objectMapper.writeValueAsString(analysis);
            checkRequestMapper.updateImagingAnalysis(id, json, analyzedAt);
            request.setImagingAnalysisResult(json);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "分析结果序列化失败", ex);
        }
        request.setImagingAnalyzedAt(analyzedAt);
        log.info("CT 影像分析完成 | checkRequestId={} volumeId={}", id, volumeId);
        Map<String, Object> response = buildImagingMap(request);
        response.put("analysisResult", analysis);
        return response;
    }

    /**
     * 执行 CT 病灶分割并持久化到检查单
     */
    @Transactional
    public Map<String, Object> segmentCheckImaging(Long id) {
        CheckRequest request = requireCtImagingContext(id, true);
        String volumeId = request.getImagingVolumeId();
        Map<String, Object> segmentData = ctViewerClient.segmentVolume(volumeId);
        LocalDateTime segmentedAt = LocalDateTime.now();

        String maskVolumeId = segmentData.get("maskVolumeId") != null
            ? String.valueOf(segmentData.get("maskVolumeId"))
            : null;
        try {
            String json = objectMapper.writeValueAsString(segmentData);
            checkRequestMapper.updateImagingSegmentation(id, json, segmentedAt, maskVolumeId);
            request.setImagingSegmentationResult(json);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "分割结果序列化失败", ex);
        }
        request.setImagingSegmentedAt(segmentedAt);
        request.setImagingSegmentationMaskVolumeId(maskVolumeId);
        log.info("CT 病灶分割完成 | checkRequestId={} volumeId={} maskVolumeId={}", id, volumeId, maskVolumeId);
        Map<String, Object> response = buildImagingMap(request);
        response.put("segmentationResult", segmentData);
        return response;
    }

    /**
     * AI 肺结节分割（调用 lung-nodule-seg-service，结果同步落库）。
     *
     * @param modelId 可选，指定使用的 AI 分割模型（monai / segnet / nnunet），为空时使用服务端默认模型
     */
    @Transactional
    public Map<String, Object> aiSegmentCheckImaging(Long id, String modelId) {
        CheckRequest request = requireCtImagingContext(id, true);
        String volumeId = request.getImagingVolumeId();
        Map<String, Object> segmentData = ctViewerClient.aiSegmentVolume(volumeId, modelId);
        LocalDateTime segmentedAt = LocalDateTime.now();

        String maskVolumeId = segmentData.get("maskVolumeId") != null
            ? String.valueOf(segmentData.get("maskVolumeId"))
            : null;
        try {
            String json = objectMapper.writeValueAsString(segmentData);
            checkRequestMapper.updateImagingSegmentation(id, json, segmentedAt, maskVolumeId);
            request.setImagingSegmentationResult(json);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "AI 分割结果序列化失败", ex);
        }
        request.setImagingSegmentedAt(segmentedAt);
        request.setImagingSegmentationMaskVolumeId(maskVolumeId);
        log.info("AI 肺结节分割完成 | checkRequestId={} volumeId={} maskVolumeId={}", id, volumeId, maskVolumeId);
        Map<String, Object> response = buildImagingMap(request);
        response.put("segmentationResult", segmentData);
        return response;
    }

    /**
     * 绑定 CT 影像 volume 到检查单
     */
    @Transactional
    public Map<String, Object> bindCheckImaging(Long id, Map<String, Object> body) {
        CheckRequest request = requireCtImagingContext(id, false);
        String volumeId = trimToNull(body != null ? (String) body.get("volumeId") : null);
        if (volumeId == null) {
            throw new BusinessException(400, "请提供 volumeId");
        }
        ctViewerClient.assertVolumeExists(volumeId);
        String sourceName = trimToNull(body != null ? (String) body.get("sourceName") : null);
        LocalDateTime uploadedAt = LocalDateTime.now();
        checkRequestMapper.updateImaging(id, volumeId, uploadedAt, sourceName);
        request.setImagingVolumeId(volumeId);
        request.setImagingUploadedAt(uploadedAt);
        request.setImagingSourceName(sourceName);
        MedicalTechnology technology = medicalTechnologyMapper.selectById(request.getMedicalTechnologyId());
        Long departmentId = technology != null ? technology.getDeptmentId() : null;
        ctViewerClient.bindVolume(volumeId, id, departmentId, request.getRegisterId());
        log.info("绑定 CT 影像 | checkRequestId={} volumeId={}", id, volumeId);
        return buildImagingMap(request);
    }

    /**
     * 清除检查单 CT 影像绑定（检查中可重新上传）
     */
    @Transactional
    public void clearCheckImaging(Long id) {
        CheckRequest request = requireCtImagingContext(id, false);
        String volumeId = request.getImagingVolumeId();
        checkRequestMapper.clearImaging(id);
        if (volumeId != null && !volumeId.isBlank()) {
            ctViewerClient.unbindVolume(volumeId.trim());
        }
        log.info("清除 CT 影像绑定 | checkRequestId={}", id);
    }

    /**
     * CT 影像业务上下文校验（供 ct-infer 等调用）
     *
     * @param requireBoundVolume 是否要求已绑定 volume
     */
    public CheckRequest requireCtImagingContext(Long checkRequestId, boolean requireBoundVolume) {
        CheckRequest request = checkRequestMapper.selectById(checkRequestId);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!"检查中".equals(request.getCheckState())) {
            throw new BusinessException(400, "当前状态不允许操作 CT 影像");
        }
        if (!CtCategoryResolver.isCt(request.getAiCategoryCode())) {
            throw new BusinessException(400, "当前检查项目不是 CT 影像");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        paymentClient.assertItemPaid(request.getRegisterId(), "CHECK_FEE", checkRequestId, "检查费");
        if (requireBoundVolume && !hasImaging(request)) {
            throw new BusinessException(400, "请先上传并绑定 CT 影像后再进行分析");
        }
        return request;
    }

    /**
     * 校验医技科室权限（供模拟/推理服务调用）
     */
    public void assertCheckDepartmentAccess(Long medicalTechnologyId) {
        assertRequestDepartmentAccess(medicalTechnologyId);
    }

    /**
     * 持久化检查模拟工作流草稿（仅更新 check_result，不改变状态）
     */
    @Transactional
    public void saveCheckSimulationDraft(Long checkRequestId, Map<String, Object> simulationData) {
        CheckRequest request = checkRequestMapper.selectById(checkRequestId);
        if (request == null || !"检查中".equals(request.getCheckState())) {
            return;
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        String payload = resultFormService.buildSimulationDraftPayload(
            request.getMedicalTechnologyId(),
            simulationData
        );
        checkRequestMapper.updateSimulationDraft(checkRequestId, payload);
    }

    /**
     * 持久化检验模拟工作流草稿（仅更新 inspection_result，不改变状态）
     */
    @Transactional
    public void saveInspectionSimulationDraft(Long inspectionRequestId, Map<String, Object> simulationData) {
        InspectionRequest request = inspectionRequestMapper.selectById(inspectionRequestId);
        if (request == null || !"检验中".equals(request.getInspectionState())) {
            return;
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        String payload = resultFormService.buildSimulationDraftPayload(
            request.getMedicalTechnologyId(),
            simulationData
        );
        inspectionRequestMapper.updateSimulationDraft(inspectionRequestId, payload);
    }

    // ==================== 检验相关 ====================

    /**
     * 获取待检验患者列表
     */
    public List<Map<String, Object>> getInspectionApplications(Long registrationId, String inspectionState) {
        Long departmentId = departmentIdFilter();
        List<InspectionRequest> requests;
        if (registrationId != null) {
            requests = inspectionRequestMapper.selectByRegisterId(registrationId, departmentId);
        } else if (inspectionState != null && !inspectionState.isBlank()) {
            requests = inspectionRequestMapper.selectByInspectionState(inspectionState.trim(), departmentId);
        } else {
            requests = inspectionRequestMapper.selectPending(departmentId);
        }
        return enrichWithPayment(requests.stream().map(this::toInspectionMap).toList(), "INSPECTION_FEE");
    }

    /**
     * 开始检验
     */
    @Transactional
    public void startInspection(Long id, Map<String, Object> operatorInfo) {
        log.info("开始检验 | inspectionRequestId={}", id);
        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        if (!"待检验".equals(request.getInspectionState())) {
            throw new BusinessException(400, "当前状态不允许开始检验");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        paymentClient.assertItemPaid(request.getRegisterId(), "INSPECTION_FEE", id, "检验费");
        Long inspectionEmployeeId = resolveOperatorEmployeeId(operatorInfo, "inspectionEmployeeId");
        if (inspectionEmployeeId != null) {
            inspectionRequestMapper.updateInspectionStateWithEmployee(id, "检验中", inspectionEmployeeId);
        } else {
            inspectionRequestMapper.updateInspectionState(id, "检验中");
        }
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
        if (!"检验中".equals(request.getInspectionState())) {
            throw new BusinessException(400, "当前状态不允许记录采样");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        inspectionRequestMapper.updateInspectionTime(id, LocalDateTime.now());
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
        if (!"检验中".equals(request.getInspectionState())) {
            throw new BusinessException(400, "当前状态不允许录入结果");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());

        String inspectionResult;
        if (resultData.get("values") instanceof Map<?, ?>) {
            inspectionResult = resultFormService.buildResultPayload(request.getMedicalTechnologyId(), resultData);
        } else {
            inspectionResult = trimToNull((String) resultData.get("inspectionResult"));
            if (inspectionResult == null) {
                inspectionResult = trimToNull((String) resultData.get("result"));
            }
            if (inspectionResult == null) {
                throw new BusinessException(400, "请填写检验结果");
            }
        }

        request.setInspectionResult(inspectionResult);
        request.setInspectionState("已完成");
        request.setInspectionTime(LocalDateTime.now());
        String remark = trimToNull((String) resultData.get("inspectionRemark"));
        if (remark == null && resultData.get("values") instanceof Map<?, ?> values) {
            Object fromValues = values.get("inspectionRemark");
            if (fromValues != null && !String.valueOf(fromValues).isBlank()) {
                remark = String.valueOf(fromValues).trim();
            }
        }
        request.setInspectionRemark(remark);
        request.setInputinspectionEmployeeId(resolveOperatorEmployeeId(resultData, "inputinspectionEmployeeId"));
        inspectionRequestMapper.updateResult(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("inspectionTime", request.getInspectionTime());
        physicianW3Client.triggerW3Async(request.getRegisterId());
        attachCriticalDetect(response, request.getTechCode(), request.getTechName(), resultData);

        return response;
    }

    /**
     * 获取检验申请详情
     */
    public Map<String, Object> getInspectionReport(Long id) {
        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        Map<String, Object> detail = toInspectionDetailMap(request);
        enrichSingleWithPayment(detail, "INSPECTION_FEE");
        return detail;
    }

    /**
     * 归档检验申请
     */
    @Transactional
    public void archiveInspection(Long id, Map<String, Object> archiveData) {
        log.info("归档检验 | inspectionRequestId={}", id);
        InspectionRequest request = inspectionRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        if (!isArchivableInspectionState(request.getInspectionState())) {
            throw new BusinessException(400, "当前状态不允许归档");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        String remark = buildArchiveRemark(archiveData);
        inspectionRequestMapper.updateArchive(id, "已归档", remark);
    }

    // ==================== 处置相关 ====================

    /**
     * 获取待处置患者列表
     */
    public List<Map<String, Object>> getDisposalApplications(Long registrationId, String disposalState) {
        Long departmentId = departmentIdFilter();
        List<DisposalRequest> requests;
        if (registrationId != null) {
            requests = disposalRequestMapper.selectByRegisterId(registrationId, departmentId);
        } else if (disposalState != null && !disposalState.isBlank()) {
            requests = disposalRequestMapper.selectByDisposalState(disposalState.trim(), departmentId);
        } else {
            requests = disposalRequestMapper.selectPending(departmentId);
        }
        return enrichWithPayment(requests.stream().map(this::toDisposalMap).toList(), "DISPOSAL_FEE");
    }

    /**
     * 开始处置
     */
    @Transactional
    public void startDisposal(Long id, Map<String, Object> operatorInfo) {
        log.info("开始处置 | disposalRequestId={}", id);
        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        if (!"待处置".equals(request.getDisposalState())) {
            throw new BusinessException(400, "当前状态不允许开始处置");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        paymentClient.assertItemPaid(request.getRegisterId(), "DISPOSAL_FEE", id, "处置费");
        Long disposalEmployeeId = resolveOperatorEmployeeId(operatorInfo, "disposalEmployeeId");
        if (disposalEmployeeId != null) {
            disposalRequestMapper.updateDisposalStateWithEmployee(id, "处置中", disposalEmployeeId);
        } else {
            disposalRequestMapper.updateDisposalState(id, "处置中");
        }
    }

    /**
     * 提交处置结果
     */
    @Transactional
    public Map<String, Object> submitDisposalResult(Long id, Map<String, Object> resultData) {
        log.info("提交处置结果 | disposalRequestId={}", id);

        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        if (!"处置中".equals(request.getDisposalState())) {
            throw new BusinessException(400, "当前状态不允许录入结果");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());

        String disposalResult = trimToNull((String) resultData.get("disposalResult"));
        if (disposalResult == null) {
            disposalResult = trimToNull((String) resultData.get("result"));
        }
        if (disposalResult == null) {
            throw new BusinessException(400, "请填写处置结果");
        }

        request.setDisposalResult(disposalResult);
        request.setDisposalState("已完成");
        request.setDisposalTime(LocalDateTime.now());
        request.setDisposalRemark(trimToNull((String) resultData.get("disposalRemark")));
        request.setInputdisposalEmployeeId(resolveOperatorEmployeeId(resultData, "inputdisposalEmployeeId"));
        disposalRequestMapper.updateResult(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("disposalTime", request.getDisposalTime());
        attachCriticalDetect(response, request.getTechCode(), request.getTechName(), resultData);
        return response;
    }

    /**
     * 获取处置申请详情
     */
    public Map<String, Object> getDisposalReport(Long id) {
        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        Map<String, Object> detail = toDisposalDetailMap(request);
        enrichSingleWithPayment(detail, "DISPOSAL_FEE");
        return detail;
    }

    /**
     * 归档处置申请
     */
    @Transactional
    public void archiveDisposal(Long id, Map<String, Object> archiveData) {
        log.info("归档处置 | disposalRequestId={}", id);
        DisposalRequest request = disposalRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        if (!isArchivableDisposalState(request.getDisposalState())) {
            throw new BusinessException(400, "当前状态不允许归档");
        }
        assertRequestDepartmentAccess(request.getMedicalTechnologyId());
        String remark = buildArchiveRemark(archiveData);
        disposalRequestMapper.updateArchive(id, "已归档", remark);
    }

    // ==================== 基础数据 ====================

    public Map<String, Object> getCurrentProfile() {
        MedtechAuthContext.Context ctx = MedtechAuthContext.get();
        if (ctx == null) {
            throw new BusinessException(401, "未授权");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", ctx.userId());
        profile.put("role", ctx.role());
        profile.put("employeeId", ctx.employeeId());
        profile.put("departmentId", ctx.departmentId());
        profile.put("departmentName", ctx.departmentName());
        profile.put("adminAllAccess", ctx.adminAllAccess());
        return profile;
    }

    public Map<String, Object> getHistoricalSummary() {
        Map<String, Object> raw = medtechStatsMapper.selectHistoricalSummary(departmentIdFilter());
        Map<String, Object> summary = new LinkedHashMap<>(raw);
        long totalChecks = toLong(raw.get("totalCompletedChecks"));
        long totalInspections = toLong(raw.get("totalCompletedInspections"));
        long totalDisposals = toLong(raw.get("totalCompletedDisposals"));
        long todayChecks = toLong(raw.get("todayCompletedChecks"));
        long todayInspections = toLong(raw.get("todayCompletedInspections"));
        long todayDisposals = toLong(raw.get("todayCompletedDisposals"));
        summary.put("totalCompletedAll", totalChecks + totalInspections + totalDisposals);
        summary.put("todayCompletedAll", todayChecks + todayInspections + todayDisposals);
        return summary;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

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
        if (refs > 0 && !java.util.Objects.equals(existing.getTechType(), input.getTechType())) {
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
        // AI 检查推荐（W2）历史记录，非正式申请单，删除目录项时一并清理
        medicalTechnologyMapper.deleteAiExamSuggestionsByTechId(id);
        resultFormMapper.deleteFieldsByOwner("tech_extension", String.valueOf(id));
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
        input.setAiCategoryCode(resolveAiCategoryCode(input));
    }

    private String resolveAiCategoryCode(MedicalTechnology input) {
        String code = trimToNull(input.getAiCategoryCode());
        if (code != null) {
            if (code.startsWith("imaging_ct") && !"check".equals(input.getTechType())) {
                throw new BusinessException(400, "CT 影像分类仅适用于检查类项目");
            }
            if ("general_lab".equals(code) && !"inspection".equals(input.getTechType())) {
                throw new BusinessException(400, "通用检验分类仅适用于检验类项目");
            }
            return code;
        }
        if ("inspection".equals(input.getTechType())) {
            return "general_lab";
        }
        if ("check".equals(input.getTechType())) {
            return "general_check";
        }
        return null;
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

    private static final Set<String> ARCHIVE_REASONS = Set.of(
        "患者未到", "医师撤单", "重复开立", "设备故障", "其他"
    );

    private static boolean isArchivableCheckState(String state) {
        return "待检查".equals(state) || "检查中".equals(state);
    }

    private static boolean isArchivableInspectionState(String state) {
        return "待检验".equals(state) || "检验中".equals(state);
    }

    private static boolean isArchivableDisposalState(String state) {
        return "待处置".equals(state) || "处置中".equals(state);
    }

    private static String validateArchiveReason(Map<String, Object> archiveData) {
        String reason = trimToNull((String) archiveData.get("reason"));
        if (reason == null || !ARCHIVE_REASONS.contains(reason)) {
            throw new BusinessException(400, "请选择有效的归档原因");
        }
        return reason;
    }

    private static String buildArchiveRemark(Map<String, Object> archiveData) {
        String reason = validateArchiveReason(archiveData);
        String base = "[已归档] " + reason;
        String extra = trimToNull((String) archiveData.get("remark"));
        return extra == null ? base : base + "；" + extra;
    }

    private void attachCriticalDetect(
        Map<String, Object> response,
        String techCode,
        String techName,
        Map<String, Object> resultData
    ) {
        if (response == null || resultData == null) {
            return;
        }
        try {
            CriticalDetectResult detect = criticalValueDetector.detect(techCode, techName, resultData);
            response.put("criticalDetect", criticalValueService.toDetectMap(detect));
        } catch (Exception e) {
            log.warn("危急值识别失败，已跳过 | techCode={} | {}", techCode, e.getMessage());
        }
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
        map.put("techType", "check");
        map.put("caseNumber", request.getCaseNumber());
        map.put("patientName", request.getPatientName());
        map.put("techName", request.getTechName());
        map.put("techCode", request.getTechCode());
        map.put("aiCategoryCode", CtCategoryResolver.normalize(request.getAiCategoryCode()));
        map.put("position", request.getCheckPosition());
        map.put("info", request.getCheckInfo());
        map.put("statusText", request.getCheckState());
        map.put("checkState", request.getCheckState());
        map.put("checkTime", request.getCheckTime());
        map.put("creationTime", request.getCreationTime());
        appendImagingFields(map, request);
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

    private static boolean hasImaging(CheckRequest request) {
        return request.getImagingVolumeId() != null && !request.getImagingVolumeId().isBlank();
    }

    private void appendImagingFields(Map<String, Object> map, CheckRequest request) {
        map.put("imagingVolumeId", request.getImagingVolumeId());
        map.put("imagingUploadedAt", request.getImagingUploadedAt());
        map.put("imagingSourceName", request.getImagingSourceName());
        map.put("imagingAnalyzedAt", request.getImagingAnalyzedAt());
        map.put("hasImaging", hasImaging(request));
        map.put("hasImagingAnalysis", hasImagingAnalysis(request));
        map.put("hasImagingSegmentation", hasImagingSegmentation(request));
        Map<String, Object> analysis = parseImagingAnalysis(request.getImagingAnalysisResult());
        if (analysis != null) {
            map.put("imagingAnalysisResult", analysis);
        }
        Map<String, Object> segmentation = parseImagingAnalysis(request.getImagingSegmentationResult());
        if (segmentation != null) {
            map.put("imagingSegmentationResult", segmentation);
        }
        map.put("imagingSegmentationMaskVolumeId", request.getImagingSegmentationMaskVolumeId());
        map.put("imagingSegmentedAt", request.getImagingSegmentedAt());
    }

    private Map<String, Object> buildImagingMap(CheckRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkRequestId", request.getId());
        map.put("volumeId", request.getImagingVolumeId());
        map.put("uploadedAt", request.getImagingUploadedAt());
        map.put("sourceName", request.getImagingSourceName());
        map.put("analyzedAt", request.getImagingAnalyzedAt());
        map.put("segmentedAt", request.getImagingSegmentedAt());
        map.put("hasImaging", hasImaging(request));
        map.put("hasImagingAnalysis", hasImagingAnalysis(request));
        map.put("hasImagingSegmentation", hasImagingSegmentation(request));
        map.put("maskVolumeId", request.getImagingSegmentationMaskVolumeId());
        Map<String, Object> analysis = parseImagingAnalysis(request.getImagingAnalysisResult());
        if (analysis != null) {
            map.put("analysisResult", analysis);
        }
        Map<String, Object> segmentation = parseImagingAnalysis(request.getImagingSegmentationResult());
        if (segmentation != null) {
            map.put("segmentationResult", segmentation);
        }
        return map;
    }

    private Map<String, Object> parseImagingAnalysis(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("解析 CT 分析结果失败", ex);
            return null;
        }
    }

    private static boolean hasImagingAnalysis(CheckRequest request) {
        return request.getImagingAnalysisResult() != null && !request.getImagingAnalysisResult().isBlank();
    }

    private static boolean hasImagingSegmentation(CheckRequest request) {
        return request.getImagingSegmentationResult() != null && !request.getImagingSegmentationResult().isBlank();
    }

    private Map<String, Object> toInspectionMap(InspectionRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("caseNumber", request.getCaseNumber());
        map.put("patientName", request.getPatientName());
        map.put("techName", request.getTechName());
        map.put("techCode", request.getTechCode());
        map.put("techType", "inspection");
        map.put("position", request.getInspectionPosition());
        map.put("info", request.getInspectionInfo());
        map.put("statusText", request.getInspectionState());
        map.put("inspectionState", request.getInspectionState());
        map.put("inspectionTime", request.getInspectionTime());
        map.put("creationTime", request.getCreationTime());
        return map;
    }

    private Map<String, Object> toInspectionDetailMap(InspectionRequest request) {
        Map<String, Object> map = toInspectionMap(request);
        map.put("medicalTechnologyId", request.getMedicalTechnologyId());
        map.put("inspectionResult", request.getInspectionResult());
        map.put("result", request.getInspectionResult());
        map.put("inspectionRemark", request.getInspectionRemark());
        return map;
    }

    private Map<String, Object> toDisposalMap(DisposalRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", request.getId());
        map.put("registerId", request.getRegisterId());
        map.put("caseNumber", request.getCaseNumber());
        map.put("patientName", request.getPatientName());
        map.put("techName", request.getTechName());
        map.put("techCode", request.getTechCode());
        map.put("techType", "disposal");
        map.put("position", request.getDisposalPosition());
        map.put("info", request.getDisposalInfo());
        map.put("statusText", request.getDisposalState());
        map.put("disposalState", request.getDisposalState());
        map.put("disposalTime", request.getDisposalTime());
        map.put("creationTime", request.getCreationTime());
        return map;
    }

    private Map<String, Object> toDisposalDetailMap(DisposalRequest request) {
        Map<String, Object> map = toDisposalMap(request);
        map.put("medicalTechnologyId", request.getMedicalTechnologyId());
        map.put("disposalResult", request.getDisposalResult());
        map.put("result", request.getDisposalResult());
        map.put("disposalRemark", request.getDisposalRemark());
        return map;
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

    private Long departmentIdFilter() {
        return MedtechAuthContext.departmentIdOrNull();
    }

    private void assertRequestDepartmentAccess(Long medicalTechnologyId) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return;
        }
        Long departmentId = MedtechAuthContext.departmentIdOrNull();
        if (departmentId == null) {
            throw new BusinessException(403, "医技账号未绑定执行科室");
        }
        MedicalTechnology technology = medicalTechnologyMapper.selectById(medicalTechnologyId);
        if (technology == null) {
            throw new BusinessException(404, "医技项目不存在");
        }
        if (technology.getDeptmentId() == null || !departmentId.equals(technology.getDeptmentId())) {
            throw new BusinessException(403, "无权操作其他科室的申请");
        }
    }

    private Long resolveOperatorEmployeeId(Map<String, Object> data, String key) {
        Long fromRequest = extractLong(data, key);
        if (fromRequest != null) {
            return fromRequest;
        }
        return MedtechAuthContext.employeeIdOrNull();
    }

    private List<Map<String, Object>> enrichWithPayment(List<Map<String, Object>> items, String itemCode) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        List<Long> registerIds = items.stream()
                .map(row -> row.get("registerId"))
                .map(MedtechService::toLong)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Map<String, Map<String, Object>> expenseIndex = paymentClient.loadExpenseIndex(registerIds, itemCode);
        for (Map<String, Object> item : items) {
            Long registerId = toLong(item.get("registerId"));
            Long sourceId = toLong(item.get("id"));
            String key = PaymentClient.expenseKey(registerId, itemCode, sourceId);
            PaymentClient.applyPaymentFields(item, expenseIndex.get(key));
        }
        return items;
    }

    private void enrichSingleWithPayment(Map<String, Object> item, String itemCode) {
        if (item == null) {
            return;
        }
        Long registerId = toLong(item.get("registerId"));
        Long sourceId = toLong(item.get("id"));
        Map<String, Map<String, Object>> expenseIndex = paymentClient.loadExpenseIndex(List.of(registerId), itemCode);
        String key = PaymentClient.expenseKey(registerId, itemCode, sourceId);
        PaymentClient.applyPaymentFields(item, expenseIndex.get(key));
    }
}
