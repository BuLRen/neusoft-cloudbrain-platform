package com.xikang.auth.service;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.entity.PatientBalanceTransaction;
import com.xikang.auth.mapper.PatientBalanceTransactionMapper;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Patient Service - 患者管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;
    private final UserPatientManagedMapper userPatientManagedMapper;
    private final PatientBalanceTransactionMapper balanceTransactionMapper;

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
        normalizeAndValidateIdentity(patient, true);
        if (userId == null) {
            throw new com.xikang.common.exception.BusinessException(400, "用户ID不能为空");
        }
        if (relation == null || relation.isBlank()) {
            throw new com.xikang.common.exception.BusinessException(400, "关系不能为空");
        }

        Integer patientId;

        Patient existing = patientMapper.selectByIdCard(patient.getIdCard());
        if (existing != null) {
            if (existing.getRealName() != null && !existing.getRealName().equals(patient.getRealName())) {
                throw new com.xikang.common.exception.BusinessException(409, "身份证号与姓名不匹配");
            }
            patientId = existing.getId();
            log.info("Patient already exists for idCard: {}, patientId: {}", patient.getIdCard(), patientId);
        } else {
            patient.setDelmark(1);
            patient.setCreateTime(LocalDateTime.now());
            patient.setUpdateTime(LocalDateTime.now());
            patientMapper.insert(patient);
            patientId = patient.getId();
            log.info("Patient created: id={}, name={}, idCard={}", patient.getId(), patient.getRealName(), patient.getIdCard());
        }

        userPatientManagedMapper.insert(userId, patientId, relation);
        log.info("User-Patient relation created: userId={}, patientId={}, relation={}", userId, patientId, relation);
    }

    public void updatePatient(Patient patient) {
        if (patient.getId() == null) {
            throw new com.xikang.common.exception.BusinessException(400, "患者ID不能为空");
        }
        normalizeAndValidateIdentity(patient, true);
        Patient sameIdCard = patientMapper.selectByIdCard(patient.getIdCard());
        if (sameIdCard != null && !sameIdCard.getId().equals(patient.getId())) {
            throw new com.xikang.common.exception.BusinessException(409, "该身份证号已存在其他患者档案");
        }
        patientMapper.update(patient);
        log.info("Patient updated: id={}", patient.getId());
    }

    public void deletePatient(Integer patientId) {
        patientMapper.deleteById(patientId);
        log.info("Patient deleted: id={}", patientId);
    }

    private void normalizeAndValidateIdentity(Patient patient, boolean phoneRequired) {
        String realName = patient.getRealName() == null ? null : patient.getRealName().trim();
        String idCard = patient.getIdCard() == null ? null : patient.getIdCard().trim().toUpperCase();
        String phone = patient.getPhone() == null ? null : patient.getPhone().trim();
        if (realName == null || realName.isBlank()) {
            throw new com.xikang.common.exception.BusinessException(400, "真实姓名不能为空");
        }
        if (!isValidChineseIdCard(idCard)) {
            throw new com.xikang.common.exception.BusinessException(400, "请输入正确的18位身份证号");
        }
        if ((phoneRequired || (phone != null && !phone.isBlank())) && (phone == null || !phone.matches("^1[3-9]\\d{9}$"))) {
            throw new com.xikang.common.exception.BusinessException(400, "请输入正确的11位手机号");
        }
        patient.setRealName(realName);
        patient.setIdCard(idCard);
        patient.setPhone(phone);
        patient.setBirthdate(parseBirthdate(idCard));
        patient.setGender(((idCard.charAt(16) - '0') % 2 == 1) ? "男" : "女");
    }

    private boolean isValidChineseIdCard(String idCard) {
        if (idCard == null || !idCard.matches("^\\d{17}[0-9X]$")) return false;
        String validProvinces = "|11|12|13|14|15|21|22|23|31|32|33|34|35|36|37|41|42|43|44|45|46|50|51|52|53|54|61|62|63|64|65|71|81|82|";
        if (!validProvinces.contains("|" + idCard.substring(0, 2) + "|")
                || "000000".equals(idCard.substring(0, 6))
                || "000".equals(idCard.substring(14, 17))) return false;
        LocalDate birthdate = parseBirthdate(idCard);
        if (birthdate == null || birthdate.isAfter(LocalDate.now()) || birthdate.isBefore(LocalDate.of(1900, 1, 1))) return false;
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (idCard.charAt(i) - '0') * weights[i];
        return idCard.charAt(17) == checks[sum % 11];
    }

    private LocalDate parseBirthdate(String idCard) {
        if (idCard == null || idCard.length() < 14) return null;
        try {
            return LocalDate.of(Integer.parseInt(idCard.substring(6, 10)), Integer.parseInt(idCard.substring(10, 12)), Integer.parseInt(idCard.substring(12, 14)));
        } catch (Exception ignored) {
            return null;
        }
    }

    public BigDecimal getBalance(Integer patientId) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return patient.getAccountBalance();
    }

    public List<Patient> searchPatients(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int size = limit < 1 ? 20 : Math.min(limit, 50);
        return patientMapper.searchByKeyword(keyword.trim(), size);
    }

    /**
     * 充值：原子更新余额并写入真实钱包流水
     */
    @Transactional
    public Map<String, Object> rechargeBalance(Integer patientId,
                                               BigDecimal amount,
                                               String businessType,
                                               Long businessId,
                                               Long operatorId,
                                               String operatorName,
                                               String remark) {
        validateAmount(amount);
        Patient patient = lockPatient(patientId);
        BigDecimal before = safeBalance(patient);
        BigDecimal after = before.add(amount);
        PatientBalanceTransaction tx = applyTransaction(patientId, amount, before, after, "RECHARGE",
                businessType, businessId, operatorId, operatorName, remark);
        patientMapper.updateBalance(patientId, after);
        log.info("Recharge balance success | patientId={}, amount={}, before={}, after={}",
                patientId, amount, before, after);
        return balanceResult(patientId, after, true, "充值成功", tx);
    }

    /**
     * 扣款：原子扣减余额并写入真实钱包流水
     * 同一业务（businessType + businessId）已存在 DEDUCT 流水时直接视为幂等成功
     */
    @Transactional
    public Map<String, Object> deductBalance(Integer patientId,
                                             BigDecimal amount,
                                             String businessType,
                                             Long businessId,
                                             Long operatorId,
                                             String operatorName,
                                             String remark) {
        validateAmount(amount);
        PatientBalanceTransaction existing = findExistingTransaction(patientId, "DEDUCT", businessType, businessId);
        if (existing != null) {
            log.info("Deduct idempotent hit | patientId={}, businessType={}, businessId={}, txNo={}",
                    patientId, businessType, businessId, existing.getTransactionNo());
            return idempotentResult(patientId, existing, "已处理");
        }

        Patient patient = lockPatient(patientId);
        BigDecimal before = safeBalance(patient);
        if (before.compareTo(amount) < 0) {
            throw new com.xikang.common.exception.BusinessException(400, "余额不足");
        }
        BigDecimal after = before.subtract(amount);

        PatientBalanceTransaction tx = applyTransaction(patientId, amount, before, after, "DEDUCT",
                businessType, businessId, operatorId, operatorName, remark);
        patientMapper.updateBalance(patientId, after);
        log.info("Deduct balance success | patientId={}, amount={}, before={}, after={}, businessType={}, businessId={}",
                patientId, amount, before, after, businessType, businessId);
        return balanceResult(patientId, after, true, "扣款成功", tx);
    }

    /**
     * 退款：原子回退余额并写入真实钱包流水
     * 同一业务（businessType + businessId）已存在 REFUND 流水时直接视为幂等成功
     */
    @Transactional
    public Map<String, Object> refundBalance(Integer patientId,
                                             BigDecimal amount,
                                             String businessType,
                                             Long businessId,
                                             Long operatorId,
                                             String operatorName,
                                             String remark) {
        validateAmount(amount);
        PatientBalanceTransaction existing = findExistingTransaction(patientId, "REFUND", businessType, businessId);
        if (existing != null) {
            log.info("Refund idempotent hit | patientId={}, businessType={}, businessId={}, txNo={}",
                    patientId, businessType, businessId, existing.getTransactionNo());
            return idempotentResult(patientId, existing, "已处理");
        }

        Patient patient = lockPatient(patientId);
        BigDecimal before = safeBalance(patient);
        BigDecimal after = before.add(amount);

        PatientBalanceTransaction tx = applyTransaction(patientId, amount, before, after, "REFUND",
                businessType, businessId, operatorId, operatorName, remark);
        patientMapper.updateBalance(patientId, after);
        log.info("Refund balance success | patientId={}, amount={}, before={}, after={}, businessType={}, businessId={}",
                patientId, amount, before, after, businessType, businessId);
        return balanceResult(patientId, after, true, "退款成功", tx);
    }

    /**
     * 查询患者钱包流水（可选按类型过滤）
     */
    public List<PatientBalanceTransaction> listBalanceTransactions(Integer patientId, String transactionType) {
        if (patientMapper.selectById(patientId) == null) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return balanceTransactionMapper.selectByPatient(patientId, transactionType);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.xikang.common.exception.BusinessException(400, "金额必须大于0");
        }
    }

    private Patient lockPatient(Integer patientId) {
        Patient patient = patientMapper.selectByIdForUpdate(patientId);
        if (patient == null) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return patient;
    }

    private BigDecimal safeBalance(Patient patient) {
        return patient.getAccountBalance() == null ? BigDecimal.ZERO : patient.getAccountBalance();
    }

    private PatientBalanceTransaction findExistingTransaction(Integer patientId,
                                                              String transactionType,
                                                              String businessType,
                                                              Long businessId) {
        if (patientId == null || transactionType == null || businessType == null || businessId == null) {
            return null;
        }
        return balanceTransactionMapper.selectByBusiness(patientId, transactionType, businessType, businessId);
    }

    private PatientBalanceTransaction applyTransaction(Integer patientId,
                                                        BigDecimal amount,
                                                        BigDecimal before,
                                                        BigDecimal after,
                                                        String transactionType,
                                                        String businessType,
                                                        Long businessId,
                                                        Long operatorId,
                                                        String operatorName,
                                                        String remark) {
        PatientBalanceTransaction tx = new PatientBalanceTransaction();
        tx.setTransactionNo(generateTransactionNo(transactionType));
        tx.setPatientId(patientId);
        tx.setTransactionType(transactionType);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setBusinessType(businessType);
        tx.setBusinessId(businessId);
        tx.setOperatorId(operatorId);
        tx.setOperatorName(operatorName);
        tx.setRemark(remark);
        tx.setTransactionTime(LocalDateTime.now());
        balanceTransactionMapper.insert(tx);
        return tx;
    }

    private Map<String, Object> balanceResult(Integer patientId,
                                              BigDecimal balance,
                                              boolean success,
                                              String message,
                                              PatientBalanceTransaction tx) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("patientId", patientId);
        result.put("accountBalance", balance);
        if (tx != null) {
            result.put("transactionNo", tx.getTransactionNo());
            result.put("transactionType", tx.getTransactionType());
            result.put("transactionTime", tx.getTransactionTime());
        }
        return result;
    }

    private Map<String, Object> idempotentResult(Integer patientId, PatientBalanceTransaction existing, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("patientId", patientId);
        result.put("accountBalance", existing.getBalanceAfter());
        result.put("transactionNo", existing.getTransactionNo());
        result.put("transactionType", existing.getTransactionType());
        result.put("transactionTime", existing.getTransactionTime());
        result.put("idempotent", true);
        return result;
    }

    private String generateTransactionNo(String transactionType) {
        String prefix;
        switch (transactionType) {
            case "RECHARGE" -> prefix = "RC";
            case "DEDUCT" -> prefix = "DT";
            case "REFUND" -> prefix = "RF";
            default -> prefix = "TX";
        }
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
