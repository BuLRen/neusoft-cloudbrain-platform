package com.xikang.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.*;
import com.xikang.registration.mapper.*;
import com.xikang.registration.feign.AuthPatientFeignClient;
import com.xikang.registration.feign.ScheduleFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RegistLevelMapper registLevelMapper;
    private final DepartmentMapper departmentMapper;
    private final SettleCategoryMapper settleCategoryMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final ObjectMapper objectMapper;
    private final ScheduleFeignClient scheduleFeignClient;
    private final AuthPatientFeignClient authPatientFeignClient;

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
            Map<String, Object> patientInfo = authPatientFeignClient.getPatient(patientIdParam);
            Map<String, Object> patientData = unwrapMapData(patientInfo, "获取患者信息失败");
            realName = getString(patientData, "realName", realName);
            gender = getString(patientData, "gender", gender);
            Object birthdateObj = patientData.get("birthdate");
            if (birthdateObj != null) {
                birthdate = LocalDate.parse(birthdateObj.toString());
            }
            cardNumber = getString(patientData, "idCard", cardNumber);
            homeAddress = getString(patientData, "homeAddress", homeAddress);
            log.info("从 patientId={} 获取患者信息成功: {}", patientIdParam, realName);
        }

        if (realName == null || realName.isBlank()) {
            throw new BusinessException(400, "患者姓名不能为空，请先完善就诊人信息");
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
        Long schedulingId = request.get("schedulingId") != null
            ? ((Number) request.get("schedulingId")).longValue()
            : null;

        String visitDateText = request.get("visitDate") != null
            ? request.get("visitDate").toString()
            : null;
        String visitTime = request.get("visitTime") != null ? request.get("visitTime").toString() : null;
        String noon = request.get("noon") != null ? request.get("noon").toString() : (visitTime != null ? visitTime : "上午");
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
        if (schedulingId == null) {
            throw new BusinessException(400, "请选择可用排班");
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

        Map<String, Object> schedule = getScheduleData(schedulingId);
        validateSchedule(schedule, schedulingId, deptmentId, employeeId, registLevelId, visitDate.toLocalDate(), noon);

        // 获取结算类别
        SettleCategory settleCategory = settleCategoryId != null
            ? settleCategoryMapper.selectById(settleCategoryId)
            : null;

        BigDecimal schedulePrice = toBigDecimal(schedule.get("price"));
        BigDecimal registMoney = schedulePrice != null ? schedulePrice : (registLevel.getPrice() != null ? registLevel.getPrice() : BigDecimal.ZERO);

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
        register.setSchedulingId(schedulingId);

        registrationMapper.insert(register);
        ExpenseRecord registrationFee = createRegistrationFee(register, patientIdParam, realName, registLevel, registMoney, operatorId);
        Map<String, Object> payment = tryBalancePayment(patientIdParam, registrationFee, registMoney);
        deductScheduleQuota(schedulingId, register.getId());

        log.info("挂号成功 | registerId={}, patient={}, department={}", register.getId(), realName, department.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("id", register.getId());
        result.put("caseNumber", caseNumber);
        result.put("realName", realName);
        result.put("gender", gender);
        result.put("departmentId", deptmentId);
        result.put("departmentName", department.getName());
        result.put("employeeId", employeeId);
        result.put("physicianId", employeeId);
        result.put("physicianName", schedule.get("physicianName"));
        result.put("schedulingId", schedulingId);
        result.put("visitDate", visitDate);
        result.put("visitTime", noon);
        result.put("noon", noon);
        result.put("registLevelId", registLevelId);
        result.put("registLevelName", registLevel.getName());
        result.put("registMoney", registMoney);
        result.put("amount", registMoney);
        result.put("settleCategoryId", settleCategoryId);
        result.put("settleCategoryName", settleCategory != null ? settleCategory.getName() : null);
        result.put("visitState", 1);
        result.put("visitStateName", "已挂号");
        result.put("status", 1);
        result.put("statusName", payment.get("payStatusName"));
        result.put("payStatus", payment.get("payStatus"));
        result.put("payStatusName", payment.get("payStatusName"));
        result.put("paymentMessage", payment.get("paymentMessage"));
        result.put("accountBalance", payment.get("accountBalance"));
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
        if (register.getSchedulingId() != null) {
            returnScheduleQuota(register.getSchedulingId());
        }
        log.info("退号成功 | registerId={}", id);
    }

    /**
     * 获取排班详情（调用 schedule-service）
     */
    public Map<String, Object> getSchedulingAvailable(Long scheduleId) {
        return getScheduleData(scheduleId);
    }

    /**
     * 获取科室的可用排班（调用 schedule-service）
     */
    public List<Map<String, Object>> getAvailableScheduling(Long departmentId, LocalDate date) {
        Map<String, Object> response = scheduleFeignClient.getAvailable(departmentId, date.toString());
        return unwrapListData(response);
    }

    /**
     * 获取挂号级别列表
     */
    public List<RegistLevel> getRegistLevels() {
        return registLevelMapper.selectAll();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getScheduleData(Long schedulingId) {
        Map<String, Object> response = scheduleFeignClient.getDetail(schedulingId);
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(400, "排班不存在");
        }
        return (Map<String, Object>) dataMap;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> unwrapListData(Map<String, Object> response) {
        Object data = response != null ? response.get("data") : null;
        if (data == null) {
            return List.of();
        }
        if (!(data instanceof List<?> list)) {
            throw new BusinessException(500, "排班服务返回格式异常");
        }
        return (List<Map<String, Object>>) list;
    }

    private void validateSchedule(Map<String, Object> schedule, Long schedulingId, Long departmentId,
                                  Long physicianId, Long registLevelId, LocalDate visitDate, String visitTime) {
        if (!"正常".equals(String.valueOf(schedule.get("status")))) {
            throw new BusinessException(400, "该排班不可挂号");
        }
        Integer availableQuota = toInteger(schedule.get("availableQuota"));
        if (availableQuota == null || availableQuota <= 0) {
            throw new BusinessException(400, "该排班号源不足");
        }
        requireEquals("排班科室与挂号科室不一致", departmentId, toLong(schedule.get("departmentId")));
        requireEquals("排班医生与挂号医生不一致", physicianId, toLong(schedule.get("physicianId")));
        requireEquals("排班挂号级别与所选级别不一致", registLevelId, toLong(schedule.get("registLevelId")));

        Object workDateObj = schedule.get("workDate");
        LocalDate scheduleDate = workDateObj instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(workDateObj));
        if (!visitDate.equals(scheduleDate)) {
            throw new BusinessException(400, "排班日期与就诊日期不一致");
        }
        String timeSlot = String.valueOf(schedule.get("timeSlot"));
        if (!timeSlot.equals(visitTime)) {
            throw new BusinessException(400, "排班时段与就诊时段不一致");
        }
    }

    private void deductScheduleQuota(Long schedulingId, Long registerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("scheduleId", schedulingId);
        body.put("count", 1);
        body.put("registerId", registerId);
        Map<String, Object> response = scheduleFeignClient.deductQuota(body);
        Map<String, Object> data = unwrapMapData(response, "号源扣减失败");
        if (!Boolean.TRUE.equals(data.get("success"))) {
            throw new BusinessException(400, String.valueOf(data.getOrDefault("message", "号源扣减失败")));
        }
    }

    private void returnScheduleQuota(Long schedulingId) {
        Map<String, Object> body = new HashMap<>();
        body.put("scheduleId", schedulingId);
        body.put("count", 1);
        Map<String, Object> response = scheduleFeignClient.returnQuota(body);
        Map<String, Object> data = unwrapMapData(response, "号源退还失败");
        if (!Boolean.TRUE.equals(data.get("success"))) {
            throw new BusinessException(400, String.valueOf(data.getOrDefault("message", "号源退还失败")));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMapData(Map<String, Object> response, String errorMessage) {
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(500, errorMessage);
        }
        return (Map<String, Object>) dataMap;
    }

    private void requireEquals(String message, Long expected, Long actual) {
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new BusinessException(400, message);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private ExpenseRecord createRegistrationFee(Register register, Integer patientId, String patientName,
                                                RegistLevel registLevel, BigDecimal registMoney, Long operatorId) {
        ExpenseRecord record = new ExpenseRecord();
        record.setRegisterId(register.getId());
        record.setPatientId(patientId != null ? patientId.longValue() : null);
        record.setPatientName(patientName);
        record.setCategoryName("挂号费");
        record.setItemId(registLevel.getId());
        record.setItemName(registLevel.getName() + "挂号费");
        record.setItemCode("REGISTRATION_FEE");
        record.setQuantity(1);
        record.setUnitPrice(registMoney);
        record.setTotalAmount(registMoney);
        record.setStatus(0);
        record.setOperatorId(operatorId);
        record.setOperatorName(register.getRegistMethod());
        record.setRemark("挂号自动生成费用");
        expenseRecordMapper.insert(record);
        return record;
    }

    private Map<String, Object> tryBalancePayment(Integer patientId, ExpenseRecord feeRecord, BigDecimal amount) {
        Map<String, Object> result = new HashMap<>();
        if (patientId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("payStatus", 0);
            result.put("payStatusName", "待缴费");
            result.put("paymentMessage", "请到收费处缴费");
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("businessType", "REGISTRATION");
        body.put("businessId", feeRecord.getRegisterId());

        Map<String, Object> response = authPatientFeignClient.deductBalance(patientId, body);
        Map<String, Object> data = unwrapMapData(response, "余额扣款失败");
        result.put("accountBalance", data.get("accountBalance"));
        if (Boolean.TRUE.equals(data.get("success"))) {
            feeRecord.setStatus(1);
            feeRecord.setPayTime(LocalDateTime.now());
            feeRecord.setOperatorName("患者余额");
            feeRecord.setRemark("患者账户余额自动支付");
            expenseRecordMapper.update(feeRecord);
            result.put("payStatus", 1);
            result.put("payStatusName", "已缴费");
            result.put("paymentMessage", "余额支付成功");
            return result;
        }

        result.put("payStatus", 0);
        result.put("payStatusName", "待缴费");
        result.put("paymentMessage", String.valueOf(data.getOrDefault("message", "余额不足，请充值后缴费")));
        return result;
    }

    private String generateCaseNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = caseCounter.incrementAndGet() % 1000;
        return "BL" + dateStr + String.format("%03d", seq);
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Map<String, Object> toMap(Register register) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", register.getId());
        map.put("patientId", register.getPatientId());
        map.put("schedulingId", register.getSchedulingId());
        map.put("caseNumber", register.getCaseNumber());
        map.put("realName", register.getRealName());
        map.put("gender", register.getGender());
        map.put("cardNumber", register.getCardNumber());
        map.put("birthdate", register.getBirthdate());
        map.put("age", register.getAge());
        map.put("ageType", register.getAgeType());
        map.put("homeAddress", register.getHomeAddress());
        map.put("visitDate", register.getVisitDate());
        map.put("visitTime", register.getNoon());
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