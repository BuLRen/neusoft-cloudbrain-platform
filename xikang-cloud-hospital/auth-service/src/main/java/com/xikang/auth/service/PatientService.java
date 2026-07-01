package com.xikang.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.auth.entity.Patient;
import com.xikang.auth.entity.PatientBalanceTransaction;
import com.xikang.auth.mapper.PatientBalanceTransactionMapper;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static final ObjectMapper DEBUG_OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEBUG_LOG_PATH = Path.of("/Users/zanderc/Code/neusoft-cloudbrain-platform/neusoft-cloudbrain-platform/.cursor/debug-02f0a8.log");

    private final PatientMapper patientMapper;
    private final UserPatientManagedMapper userPatientManagedMapper;
    private final PatientBalanceTransactionMapper balanceTransactionMapper;
    private final Environment environment;
    private final DataSource dataSource;

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

        Patient existing = patientMapper.selectByIdCard(patient.getIdCard());
        if (existing != null) {
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
        patientMapper.update(patient);
        log.info("Patient updated: id={}", patient.getId());
    }

    public void deletePatient(Integer patientId) {
        patientMapper.deleteById(patientId);
        log.info("Patient deleted: id={}", patientId);
    }

    public BigDecimal getBalance(Integer patientId) {
        Patient patient = patientMapper.selectById(patientId);
        // region agent log
        debugLog("initial", "H1,H2,H3,H5", "PatientService.java:getBalance",
                "auth balance lookup result", debugData(
                        "patientId", patientId,
                        "found", patient != null,
                        "delmark", patient != null ? patient.getDelmark() : null,
                        "activeProfiles", String.join(",", environment.getActiveProfiles()),
                        "dataSource", describeDataSource()
                ));
        // endregion
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
        // region agent log
        debugLog("initial", "H1,H2,H3,H5", "PatientService.java:lockPatient",
                "auth lock patient result before balance mutation", debugData(
                        "patientId", patientId,
                        "found", patient != null,
                        "delmark", patient != null ? patient.getDelmark() : null,
                        "activeProfiles", String.join(",", environment.getActiveProfiles()),
                        "dataSource", describeDataSource()
                ));
        // endregion
        if (patient == null) {
            throw new com.xikang.common.exception.BusinessException(404, "患者不存在");
        }
        return patient;
    }

    private BigDecimal safeBalance(Patient patient) {
        return patient.getAccountBalance() == null ? BigDecimal.ZERO : patient.getAccountBalance();
    }

    private String describeDataSource() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDataSource) {
            return redactJdbcUrl(hikariDataSource.getJdbcUrl());
        }
        return dataSource.getClass().getName();
    }

    private static String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]+", "$1***");
    }

    private static Map<String, Object> debugData(Object... values) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            data.put(String.valueOf(values[i]), values[i + 1]);
        }
        return data;
    }

    private static void debugLog(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", "02f0a8");
            payload.put("runId", runId);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data);
            payload.put("timestamp", System.currentTimeMillis());
            Files.writeString(DEBUG_LOG_PATH, DEBUG_OBJECT_MAPPER.writeValueAsString(payload) + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
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
