package com.xikang.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.*;
import com.xikang.registration.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registration Service - 挂号服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final SchedulingMapper schedulingMapper;
    private final RegistLevelMapper registLevelMapper;
    private final DepartmentMapper departmentMapper;
    private final SettleCategoryMapper settleCategoryMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    // 用于生成病历号
    private static final AtomicLong caseCounter = new AtomicLong(System.currentTimeMillis() % 100000);

    /**
     * 创建挂号记录
     */
    @Transactional
    public Map<String, Object> createRegistration(Map<String, Object> request) {
        log.info("创建挂号 | request={}", request);

        // 支持通过 patientId 自动获取患者信息
        Integer patientIdParam = request.get("patientId") != null
            ? ((Number) request.get("patientId")).intValue()
            : null;

        String realName = (String) request.get("patientName");
        String gender = (String) request.get("gender");
        String cardNumber = (String) request.get("cardNumber");
        LocalDate birthdate = request.get("birthdate") != null
            ? LocalDate.parse(request.get("birthdate").toString())
            : null;
        Integer age = request.get("age") != null
            ? ((Number) request.get("age")).intValue()
            : null;
        String ageType = (String) request.get("ageType");
        String homeAddress = (String) request.get("homeAddress");

        // 如果传了 patientId，通过 auth-service 获取患者信息
        if (patientIdParam != null && (realName == null || realName.isBlank())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> patientInfo = restTemplate.getForObject(
                    "http://auth-service/api/patient/" + patientIdParam,
                    Map.class
                );
                if (patientInfo != null) {
                    realName = (String) patientInfo.getOrDefault("realName", realName);
                    gender = (String) patientInfo.getOrDefault("gender", gender);
                    Object birthdateObj = patientInfo.get("birthdate");
                    if (birthdateObj != null) {
                        birthdate = LocalDate.parse(birthdateObj.toString());
                    }
                    cardNumber = (String) patientInfo.getOrDefault("idCard", cardNumber);
                    homeAddress = (String) patientInfo.getOrDefault("homeAddress", homeAddress);
                    log.info("从 patientId={} 获取患者信息成功: {}", patientIdParam, realName);
                }
            } catch (Exception e) {
                log.warn("获取患者信息失败 patientId={}: {}", patientIdParam, e.getMessage());
            }
        }

        // 计算年龄（如果没有传入且有出生日期）
        if (age == null && birthdate != null) {
            age = (int) ChronoUnit.YEARS.between(birthdate, LocalDate.now());
            ageType = "年";
        }

        Long deptmentId = ((Number) request.get("departmentId")).longValue();
        Long employeeId = request.get("physicianId") != null
            ? ((Number) request.get("physicianId")).longValue()
            : null;
        Long registLevelId = ((Number) request.get("registLevelId")).longValue();
        Long settleCategoryId = request.get("settleCategoryId") != null
            ? ((Number) request.get("settleCategoryId")).longValue()
            : null;

        String visitDateText = request.get("visitDate") != null
            ? request.get("visitDate").toString()
            : null;
        String noon = (String) request.getOrDefault("noon", "上午");
        String isBook = (String) request.getOrDefault("isBook", "否");
        String registMethod = (String) request.getOrDefault("registMethod", "线上");
        String aiTriageResult = null;
        Object aiTriageResultRaw = request.get("aiTriageResult");
        if (aiTriageResultRaw != null) {
            try {
                aiTriageResult = objectMapper.writeValueAsString(aiTriageResultRaw);
            } catch (JsonProcessingException e) {
                log.warn("序列化AI导诊结果失败", e);
            }
        }
        Long operatorId = request.get("operatorId") != null
            ? ((Number) request.get("operatorId")).longValue()
            : null;

        if (deptmentId == null) {
            throw new BusinessException(400, "请选择科室");
        }
        if (registLevelId == null) {
            throw new BusinessException(400, "请选择挂号级别");
        }

        LocalDateTime visitDate = visitDateText != null && !visitDateText.isBlank()
            ? LocalDateTime.parse(visitDateText + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : LocalDateTime.now();

        // 获取科室信息
        Department department = departmentMapper.selectById(deptmentId);
        if (department == null) {
            throw new BusinessException(400, "科室不存在");
        }

        // 获取挂号级别
        RegistLevel registLevel = registLevelMapper.selectById(registLevelId);
        if (registLevel == null) {
            throw new BusinessException(400, "挂号级别不存在");
        }

        // 获取结算类别
        SettleCategory settleCategory = settleCategoryId != null
            ? settleCategoryMapper.selectById(settleCategoryId)
            : null;

        BigDecimal registMoney = registLevel.getPrice() != null ? registLevel.getPrice() : BigDecimal.ZERO;

        // 生成病历号
        String caseNumber = generateCaseNumber();

        // 构建挂号记录
        Register register = new Register();
        register.setCaseNumber(caseNumber);
        register.setRealName(realName);
        register.setGender(gender);
        register.setCardNumber(cardNumber);
        register.setBirthdate(birthdate);
        register.setAge(age);
        register.setAgeType(ageType);
        register.setHomeAddress(homeAddress);
        register.setVisitDate(visitDate);
        register.setNoon(noon);
        register.setDeptmentId(deptmentId);
        register.setEmployeeId(employeeId);
        register.setRegistLevelId(registLevelId);
        register.setSettleCategoryId(settleCategoryId);
        register.setIsBook(isBook);
        register.setRegistMethod(registMethod);
        register.setRegistMoney(registMoney);
        register.setVisitState(1); // 1=已挂号
        register.setPatientId(patientIdParam != null ? patientIdParam.longValue() : null);

        registrationMapper.insert(register);

        log.info("挂号成功 | registerId={}, patient={}, department={}", register.getId(), realName, department.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("id", register.getId());
        result.put("caseNumber", caseNumber);
        result.put("realName", realName);
        result.put("gender", gender);
        result.put("departmentId", deptmentId);
        result.put("departmentName", department.getName());
        result.put("employeeId", employeeId);
        result.put("visitDate", visitDate);
        result.put("noon", noon);
        result.put("registLevelId", registLevelId);
        result.put("registLevelName", registLevel.getName());
        result.put("registMoney", registMoney);
        result.put("settleCategoryId", settleCategoryId);
        result.put("settleCategoryName", settleCategory != null ? settleCategory.getName() : null);
        result.put("visitState", 1);
        result.put("visitStateName", "已挂号");
        result.put("patientId", patientIdParam);

        return result;
    }

    /**
     * 获取挂号详情
     */
    public Map<String, Object> getRegistration(Long id) {
        Register register = registrationMapper.selectById(id);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        return toMap(register);
    }

    /**
     * 获取患者的挂号列表
     */
    public List<Map<String, Object>> listRegistrationsByPatient(Long patientId) {
        List<Register> registers = registrationMapper.selectByPatientId(patientId);
        return registers.stream().map(this::toMap).toList();
    }

    /**
     * 按日期查询挂号列表
     */
    public List<Map<String, Object>> listRegistrationsByDate(LocalDate date) {
        List<Register> registers = registrationMapper.selectByDate(date);
        return registers.stream().map(this::toMap).toList();
    }

    /**
     * 取消挂号（退号）
     */
    @Transactional
    public void cancelRegistration(Long id) {
        log.info("取消挂号 | registerId={}", id);

        Register register = registrationMapper.selectById(id);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }

        // 校验状态：只有已挂号(1)或医生接诊(2)可以取消
        if (register.getVisitState() > 2) {
            throw new BusinessException(400, "该挂号状态不允许取消");
        }

        registrationMapper.updateStatus(id, 4); // visit_state=4 已退号
        log.info("退号成功 | registerId={}", id);
    }

    /**
     * 获取排班可用号源（兼容方法，scheduling表不存储号源）
     */
    public Map<String, Object> getSchedulingAvailable(Long schedulingId) {
        Scheduling scheduling = schedulingMapper.selectById(schedulingId);
        if (scheduling == null) {
            throw new BusinessException(404, "排班不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", scheduling.getId());
        result.put("ruleName", scheduling.getRuleName());
        result.put("weekRule", scheduling.getWeekRule());

        return result;
    }

    /**
     * 获取科室的可用排班（兼容方法）
     */
    public List<Map<String, Object>> getAvailableScheduling(Long departmentId, LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectAvailableByDepartmentAndDate(departmentId, date);
        return schedulings.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("ruleName", s.getRuleName());
            map.put("weekRule", s.getWeekRule());
            return map;
        }).toList();
    }

    /**
     * 获取挂号级别列表
     */
    public List<RegistLevel> getRegistLevels() {
        return registLevelMapper.selectAll();
    }

    private String generateCaseNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = caseCounter.incrementAndGet() % 1000;
        return "BL" + dateStr + String.format("%03d", seq);
    }

    private Map<String, Object> toMap(Register register) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", register.getId());
        map.put("caseNumber", register.getCaseNumber());
        map.put("realName", register.getRealName());
        map.put("gender", register.getGender());
        map.put("cardNumber", register.getCardNumber());
        map.put("birthdate", register.getBirthdate());
        map.put("age", register.getAge());
        map.put("ageType", register.getAgeType());
        map.put("homeAddress", register.getHomeAddress());
        map.put("visitDate", register.getVisitDate());
        map.put("noon", register.getNoon());
        map.put("deptmentId", register.getDeptmentId());
        map.put("employeeId", register.getEmployeeId());
        map.put("registLevelId", register.getRegistLevelId());
        map.put("settleCategoryId", register.getSettleCategoryId());
        map.put("isBook", register.getIsBook());
        map.put("registMethod", register.getRegistMethod());
        map.put("registMoney", register.getRegistMoney());
        map.put("visitState", register.getVisitState());
        map.put("visitStateName", getStateName(register.getVisitState()));
        return map;
    }

    private String getStateName(Integer state) {
        return switch (state) {
            case 1 -> "已挂号";
            case 2 -> "医生接诊";
            case 3 -> "看诊结束";
            case 4 -> "已退号";
            default -> "未知";
        };
    }
}