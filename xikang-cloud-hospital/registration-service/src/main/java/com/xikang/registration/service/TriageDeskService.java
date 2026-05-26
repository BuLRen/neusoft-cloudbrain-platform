package com.xikang.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.TriageDeskRecord;
import com.xikang.registration.mapper.TriageDeskRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triage Desk Service - AI分诊台服务
 * 汇总导诊结果，支撑管理员或挂号人员进行人工确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageDeskService {

    private final TriageDeskRecordMapper triageDeskRecordMapper;
    private final AiTriageClient aiTriageClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建分诊记录（从AI导诊结果创建）
     */
    @Transactional
    public Map<String, Object> createTriageRecord(Map<String, Object> triageData) {
        log.info("创建分诊记录: {}", triageData);

        Long patientId = ((Number) triageData.get("patientId")).longValue();
        String patientName = (String) triageData.get("patientName");
        String patientPhone = (String) triageData.get("patientPhone");
        String symptoms = (String) triageData.get("symptoms");
        Long operatorId = triageData.get("operatorId") != null
            ? ((Number) triageData.get("operatorId")).longValue()
            : null;
        String operatorName = (String) triageData.get("operatorName");

        // 调用AI导诊服务获取结果
        Map<String, Object> aiResult = aiTriageClient.analyzeSymptoms(symptoms, patientId);

        // 创建分诊记录
        TriageDeskRecord record = new TriageDeskRecord();
        record.setPatientId(patientId);
        record.setPatientName(patientName);
        record.setPatientPhone(patientPhone);
        record.setSymptoms(symptoms);
        try {
            record.setAiTriageResult(objectMapper.writeValueAsString(aiResult));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("AI分诊结果序列化失败", e);
            record.setAiTriageResult("{}");
        }

        record.setRecommendedDepartment((String) aiResult.get("recommendedDepartment"));
        if (aiResult.get("recommendedDepartmentId") != null) {
            record.setRecommendedDepartmentId(((Number) aiResult.get("recommendedDepartmentId")).longValue());
        }

        List<Map<String, Object>> doctors = (List<Map<String, Object>>) aiResult.get("recommendedDoctors");
        if (doctors != null && !doctors.isEmpty()) {
            Map<String, Object> firstDoctor = doctors.get(0);
            record.setRecommendedPhysicianId(firstDoctor.get("id") != null
                ? ((Number) firstDoctor.get("id")).longValue()
                : null);
            record.setRecommendedPhysicianName((String) firstDoctor.get("name"));
        }

        record.setRiskLevel((String) aiResult.getOrDefault("riskLevel", "low"));
        try {
            record.setAiAnalysis(objectMapper.writeValueAsString(aiResult.get("aiAnalysis")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("AI分析结果序列化失败", e);
            record.setAiAnalysis("{}");
        }
        record.setStatus(0); // 待确认
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setCreateTime(LocalDateTime.now());

        triageDeskRecordMapper.insert(record);

        log.info("分诊记录创建成功: id={}", record.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", record.getId());
        result.put("patientName", patientName);
        result.put("recommendedDepartment", record.getRecommendedDepartment());
        result.put("recommendedPhysicianName", record.getRecommendedPhysicianName());
        result.put("riskLevel", record.getRiskLevel());
        result.put("aiResult", aiResult);
        result.put("status", 0);
        result.put("statusName", "待确认");

        return result;
    }

    /**
     * 获取待确认分诊列表
     */
    public List<Map<String, Object>> getPendingRecords() {
        List<TriageDeskRecord> records = triageDeskRecordMapper.selectPending();
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 获取分诊记录详情
     */
    public Map<String, Object> getRecordDetail(Long id) {
        TriageDeskRecord record = triageDeskRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "分诊记录不存在");
        }
        return toDetailMap(record);
    }

    /**
     * 确认分诊结果
     */
    @Transactional
    public Map<String, Object> confirmTriage(Long id, Map<String, Object> confirmData) {
        log.info("确认分诊记录: id={}, data={}", id, confirmData);

        TriageDeskRecord record = triageDeskRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "分诊记录不存在");
        }
        if (record.getStatus() > 1) {
            throw new BusinessException(400, "该记录已确认或已挂号，无法修改");
        }

        Long departmentId = ((Number) confirmData.get("departmentId")).longValue();
        String departmentName = (String) confirmData.get("departmentName");
        Long physicianId = confirmData.get("physicianId") != null
            ? ((Number) confirmData.get("physicianId")).longValue()
            : null;
        String physicianName = (String) confirmData.get("physicianName");
        Long operatorId = confirmData.get("operatorId") != null
            ? ((Number) confirmData.get("operatorId")).longValue()
            : null;
        String operatorName = (String) confirmData.get("operatorName");
        String remark = (String) confirmData.getOrDefault("remark", "");

        triageDeskRecordMapper.updateConfirmation(id, departmentId, departmentName,
            physicianId, physicianName, operatorId, operatorName, remark);

        log.info("分诊确认成功: id={}", id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", 1);
        result.put("statusName", "已确认");
        result.put("confirmedDepartment", departmentName);
        result.put("confirmedPhysicianName", physicianName);

        return result;
    }

    /**
     * 取消分诊记录
     */
    @Transactional
    public void cancelTriage(Long id, String reason) {
        log.info("取消分诊记录: id={}, reason={}", id, reason);

        TriageDeskRecord record = triageDeskRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "分诊记录不存在");
        }

        triageDeskRecordMapper.updateStatus(id, 3); // 已取消
        log.info("分诊记录已取消: id={}", id);
    }

    /**
     * 获取患者的历史分诊记录
     */
    public List<Map<String, Object>> getPatientRecords(Long patientId) {
        List<TriageDeskRecord> records = triageDeskRecordMapper.selectByPatientId(patientId);
        return records.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(TriageDeskRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("patientId", record.getPatientId());
        map.put("patientName", record.getPatientName());
        map.put("patientPhone", record.getPatientPhone());
        map.put("symptoms", record.getSymptoms());
        map.put("recommendedDepartment", record.getRecommendedDepartment());
        map.put("recommendedDepartmentId", record.getRecommendedDepartmentId());
        map.put("recommendedPhysicianName", record.getRecommendedPhysicianName());
        map.put("recommendedPhysicianId", record.getRecommendedPhysicianId());
        map.put("riskLevel", record.getRiskLevel());
        map.put("riskLevelName", getRiskLevelName(record.getRiskLevel()));
        map.put("status", record.getStatus());
        map.put("statusName", getStatusName(record.getStatus()));
        map.put("confirmedDepartment", record.getConfirmedDepartment());
        map.put("confirmedPhysicianName", record.getConfirmedPhysicianName());
        map.put("createTime", record.getCreateTime());
        map.put("confirmTime", record.getConfirmTime());
        return map;
    }

    private Map<String, Object> toDetailMap(TriageDeskRecord record) {
        Map<String, Object> map = toMap(record);
        try {
            if (record.getAiTriageResult() != null) {
                map.put("aiTriageResult", objectMapper.readValue(record.getAiTriageResult(), Map.class));
            }
            if (record.getAiAnalysis() != null) {
                map.put("aiAnalysis", objectMapper.readValue(record.getAiAnalysis(), Map.class));
            }
        } catch (Exception e) {
            log.error("解析AI结果失败", e);
        }
        return map;
    }

    private String getRiskLevelName(String level) {
        if (level == null) return "未知";
        return switch (level.toLowerCase()) {
            case "low" -> "低风险";
            case "medium" -> "中风险";
            case "high" -> "高风险";
            default -> "未知";
        };
    }

    private String getStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待确认";
            case 1 -> "已确认";
            case 2 -> "已挂号";
            case 3 -> "已取消";
            default -> "未知";
        };
    }
}
