package com.xikang.auth.service;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Patient Service - 患者管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;
    private final UserPatientManagedMapper userPatientManagedMapper;

    /**
     * 根据用户ID获取该用户管理的患者列表（本人+家人）
     */
    public List<Patient> getPatientsByUserId(Long userId) {
        log.info("Get patients for userId: {}", userId);
        return patientMapper.selectByUserId(userId);
    }

    /**
     * 根据ID获取患者信息
     */
    public Patient getPatientById(Integer patientId) {
        return patientMapper.selectById(patientId);
    }

    /**
     * 根据身份证号获取患者信息
     */
    public Patient getPatientByIdCard(String idCard) {
        return patientMapper.selectByIdCard(idCard);
    }

    /**
     * 新增患者档案（用于添加家人）
     * 会检查身份证号是否已存在，已存在则直接关联，不存在则新建
     */
    public void createPatientWithRelation(Long userId, Patient patient, String relation) {
        // 身份证号必填
        if (patient.getIdCard() == null || patient.getIdCard().isBlank()) {
            throw new com.xikang.common.exception.BusinessException(400, "身份证号不能为空");
        }
        if (userId == null) {
            throw new com.xikang.common.exception.BusinessException(400, "用户ID不能为空");
        }
        if (relation == null || relation.isBlank()) {
            throw new com.xikang.common.exception.BusinessException(400, "关系不能为空");
        }

        Integer patientId;

        // 检查身份证号是否已存在
        Patient existing = patientMapper.selectByIdCard(patient.getIdCard());
        if (existing != null) {
            // 已存在，直接关联
            patientId = existing.getId();
            log.info("Patient already exists for idCard: {}, patientId: {}", patient.getIdCard(), patientId);
        } else {
            // 不存在，新建患者档案
            patient.setDelmark(1);
            patient.setCreateTime(java.time.LocalDateTime.now());
            patient.setUpdateTime(java.time.LocalDateTime.now());
            patientMapper.insert(patient);
            patientId = patient.getId();
            log.info("Patient created: id={}, name={}, idCard={}", patient.getId(), patient.getRealName(), patient.getIdCard());
        }

        // 建立关联关系
        userPatientManagedMapper.insert(userId, patientId, relation);
        log.info("User-Patient relation created: userId={}, patientId={}, relation={}", userId, patientId, relation);
    }

    /**
     * 更新患者档案（支持部分字段更新）
     */
    public void updatePatient(Patient patient) {
        if (patient.getId() == null) {
            throw new com.xikang.common.exception.BusinessException(400, "患者ID不能为空");
        }
        patientMapper.update(patient);
        log.info("Patient updated: id={}", patient.getId());
    }

    /**
     * 删除患者档案（软删除）
     */
    public void deletePatient(Integer patientId) {
        patientMapper.deleteById(patientId);
        log.info("Patient deleted: id={}", patientId);
    }

    public BigDecimal getBalance(Integer patientId) {
        BigDecimal balance = patientMapper.selectBalanceById(patientId);
        if (balance == null) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return balance;
    }

    @Transactional
    public Map<String, Object> rechargeBalance(Integer patientId, BigDecimal amount) {
        validateAmount(amount);
        int updated = patientMapper.rechargeBalance(patientId, amount);
        if (updated == 0) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return balanceResult(patientId, true, "充值成功");
    }

    @Transactional
    public Map<String, Object> deductBalance(Integer patientId, BigDecimal amount) {
        validateAmount(amount);
        int updated = patientMapper.deductBalanceIfEnough(patientId, amount);
        if (updated == 0) {
            BigDecimal balance = patientMapper.selectBalanceById(patientId);
            if (balance == null) {
                throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
            }
            Map<String, Object> result = balanceResult(patientId, false, "余额不足");
            result.put("accountBalance", balance);
            return result;
        }
        return balanceResult(patientId, true, "扣款成功");
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.xikang.common.exception.BusinessException(400, "金额必须大于0");
        }
    }

    private Map<String, Object> balanceResult(Integer patientId, boolean success, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("patientId", patientId);
        result.put("accountBalance", getBalance(patientId));
        return result;
    }
}