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
    private final EmployeeMapper employeeMapper;
    private final SettleCategoryMapper settleCategoryMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final ObjectMapper objectMapper;
    private final ScheduleFeignClient scheduleFeignClient;
    private final AuthPatientFeignClient authPatientFeignClient;
    private final RefundService refundService;

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

        // 生成病历号（同一患者复用首次挂号的病历号）
        String caseNumber = generateCaseNumber(patientIdParam != null ? patientIdParam.longValue() : null);

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

        // #region agent log
        Long maxRegisterId = registrationMapper.selectMaxId();
        Long nextSequenceValue = registrationMapper.selectNextSequenceValue();
        try (var writer = new java.io.FileWriter("/Users/zanderc/Code/neusoft-cloudbrain-platform/neusoft-cloudbrain-platform/.cursor/debug-7f73ef.log", true)) {
            writer.write("{\"sessionId\":\"7f73ef\",\"runId\":\"post-fix\",\"hypothesisId\":\"A\",\"location\":\"RegistrationService.java:createRegistration:beforeInsert\",\"message\":\"register id sequence state before insert\",\"data\":{\"maxRegisterId\":" + maxRegisterId + ",\"nextSequenceValue\":" + nextSequenceValue + ",\"registerIdBeforeInsert\":" + register.getId() + ",\"patientId\":" + patientIdParam + ",\"sequenceOutOfSync\":" + (nextSequenceValue <= maxRegisterId) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {
        }
        // #endregion

        if (nextSequenceValue <= maxRegisterId) {
            Long syncedSequenceValue = registrationMapper.syncIdSequence();
            nextSequenceValue = syncedSequenceValue + 1;
            log.warn("register_id_seq 与表内最大 id 不一致，已自动同步 | maxId={}, syncedSeq={}", maxRegisterId, syncedSequenceValue);
            // #region agent log
            try (var writer = new java.io.FileWriter("/Users/zanderc/Code/neusoft-cloudbrain-platform/neusoft-cloudbrain-platform/.cursor/debug-7f73ef.log", true)) {
                writer.write("{\"sessionId\":\"7f73ef\",\"runId\":\"post-fix\",\"hypothesisId\":\"A\",\"location\":\"RegistrationService.java:createRegistration:syncSequence\",\"message\":\"register id sequence synced before insert\",\"data\":{\"maxRegisterId\":" + maxRegisterId + ",\"syncedSequenceValue\":" + syncedSequenceValue + ",\"nextSequenceValueAfterSync\":" + nextSequenceValue + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            } catch (Exception ignored) {
            }
            // #endregion
        }

        registrationMapper.insert(register);

        // #region agent log
        try (var writer = new java.io.FileWriter("/Users/zanderc/Code/neusoft-cloudbrain-platform/neusoft-cloudbrain-platform/.cursor/debug-7f73ef.log", true)) {
            writer.write("{\"sessionId\":\"7f73ef\",\"runId\":\"post-fix\",\"hypothesisId\":\"A\",\"location\":\"RegistrationService.java:createRegistration:afterInsert\",\"message\":\"register insert succeeded\",\"data\":{\"assignedRegisterId\":" + register.getId() + ",\"patientId\":" + patientIdParam + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {
        }
        // #endregion
        ExpenseRecord registrationFee = createRegistrationFee(register, patientIdParam, realName, registLevel, registMoney, operatorId);
        Map<String, Object> payment = tryBalancePayment(patientIdParam, registrationFee, registMoney);
        // 清理同一挂号单上重复的待缴费 REGISTRATION_FEE，避免前端再次"去缴费"或"取消挂号"时把多条一起扣/退
        invalidateDuplicateRegistrationFees(register.getId(), registrationFee.getId());
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
        result.put("statusName", getStateName(1));
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
        Map<String, Object> result = toMap(register);
        result.put("expenseRecords", expenseRecordMapper.selectByRegisterId(id).stream().map(this::toExpenseRecordMap).toList());
        return result;
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
    public Map<String, Object> cancelRegistration(Long id) {
        log.info("取消挂号 | registerId={}", id);

        Register register = registrationMapper.selectById(id);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }

        if (register.getVisitState() == null) {
            throw new BusinessException(400, "挂号状态异常");
        }
        if (register.getVisitState() == 4) {
            throw new BusinessException(400, "该挂号已退号");
        }
        if (register.getVisitState() == 7) {
            throw new BusinessException(400, "该挂号已爽约，不可取消，如需就诊请重新挂号");
        }
        if (register.getVisitState() > 2) {
            throw new BusinessException(400, "该挂号状态不允许取消");
        }

        Map<String, Object> refundResult = refundService.refundRegistrationFee(
                id,
                register.getPatientId(),
                "患者退号",
                "患者取消挂号自动退款"
        );

        registrationMapper.updateStatus(id, 4);
        if (register.getSchedulingId() != null) {
            returnScheduleQuota(register.getSchedulingId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", id);
        result.put("status", 4);
        result.put("statusName", "已退号");
        result.put("refundAmount", refundResult.getOrDefault("refundAmount", BigDecimal.ZERO));
        result.put("refundTime", refundResult.get("refundTime"));
        result.put("accountBalance", refundResult.get("accountBalance"));
        result.put("paymentMessage", refundResult.getOrDefault("message", "取消成功"));
        result.put("refunded", Boolean.TRUE.equals(refundResult.get("success")));

        log.info("退号成功 | registerId={}, refunded={}", id, result.get("refunded"));
        return result;
    }

    /**
     * 患者到院扫码报到
     * - 校验挂号存在、状态合法（必须 visit_state = 1 已挂号）
     * - 校验就诊日期是当天
     * - 已报到则幂等返回（不报错），返回原号序
     * - 未报到则写入 check_in_time，返回号序和前面等待人数
     */
    @Transactional
    public Map<String, Object> checkIn(Long id) {
        log.info("患者报到 | registerId={}", id);

        Register register = registrationMapper.selectById(id);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (register.getVisitState() == null) {
            throw new BusinessException(400, "挂号状态异常");
        }
        if (register.getVisitState() != 1) {
            throw new BusinessException(400, "该挂号当前状态不允许报到：" + getStateName(register.getVisitState()));
        }

        // 校验就诊日期是当天
        LocalDateTime visitDate = register.getVisitDate();
        if (visitDate == null) {
            throw new BusinessException(400, "挂号缺少就诊时间，无法报到");
        }
        LocalDate visitDay = visitDate.toLocalDate();
        LocalDate today = LocalDate.now();
        if (!visitDay.equals(today)) {
            throw new BusinessException(400, "非就诊当日，无法报到（就诊日：" + visitDay + "）");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", id);
        result.put("patientName", register.getRealName());

        // 幂等：已报到直接返回原号序
        if (register.getCheckInTime() != null) {
            int before = registrationMapper.countWaitingBefore(id);
            result.put("checkInTime", register.getCheckInTime());
            result.put("alreadyCheckedIn", true);
            result.put("queueNumber", before + 1);
            result.put("waitingAhead", before);
            result.put("message", "您已报到，无需重复报到");
            log.info("重复报到（幂等返回）| registerId={}, queueNumber={}", id, before + 1);
            return result;
        }

        // 写入报到时间
        LocalDateTime now = LocalDateTime.now();
        registrationMapper.updateCheckInTime(id, now);

        int before = registrationMapper.countWaitingBefore(id);
        result.put("checkInTime", now);
        result.put("alreadyCheckedIn", false);
        result.put("queueNumber", before + 1);
        result.put("waitingAhead", before);
        result.put("message", "报到成功");
        log.info("报到成功 | registerId={}, queueNumber={}, waitingAhead={}", id, before + 1, before);
        return result;
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
        body.put("operatorId", patientId);
        body.put("operatorName", "患者余额");
        body.put("remark", "挂号时自动使用余额支付");

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

    private String generateCaseNumber(Long patientId) {
        if (patientId != null) {
            String existing = registrationMapper.selectLatestCaseNumberByPatient(patientId);
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
        }
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = caseCounter.incrementAndGet() % 1000;
        return "BL" + dateStr + String.format("%03d", seq);
    }

    /**
     * 清理同一挂号单上重复的待缴费 REGISTRATION_FEE。
     * 保留 keepId（通常是本次新建的那条），把其他 status=0 的 REGISTRATION_FEE 标记为已作废（status=3），
     * 防止前端"去缴费"/"取消挂号"时把多条一起扣/退造成 N 倍金额异常。
     */
    private void invalidateDuplicateRegistrationFees(Long registerId, Long keepId) {
        if (registerId == null) return;
        List<ExpenseRecord> pendingFees = expenseRecordMapper.selectByRegisterId(registerId).stream()
                .filter(record -> "REGISTRATION_FEE".equals(record.getItemCode()))
                .filter(record -> record.getStatus() != null && record.getStatus() == 0)
                .toList();
        if (pendingFees.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (ExpenseRecord record : pendingFees) {
            if (keepId != null && keepId.equals(record.getId())) {
                continue;
            }
            record.setStatus(3);
            record.setRemark("重复挂号费作废");
            expenseRecordMapper.update(record);
        }
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Map<String, Object> toMap(Register register) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", register.getId());
        map.put("patientId", register.getPatientId());
        map.put("patientName", register.getRealName());
        map.put("schedulingId", register.getSchedulingId());
        map.put("caseNumber", register.getCaseNumber());
        map.put("realName", register.getRealName());
        map.put("gender", register.getGender());
        map.put("cardNumber", register.getCardNumber());
        map.put("birthdate", register.getBirthdate());
        map.put("age", register.getAge());
        map.put("ageType", register.getAgeType());
        map.put("homeAddress", register.getHomeAddress());
        map.put("visitDate", register.getVisitDate() != null ? register.getVisitDate().toLocalDate() : null);
        map.put("visitTime", register.getNoon());
        map.put("noon", register.getNoon());
        map.put("deptmentId", register.getDeptmentId());
        map.put("departmentId", register.getDeptmentId());
        if (register.getDeptmentId() != null) {
            Department department = departmentMapper.selectById(register.getDeptmentId());
            map.put("departmentName", department != null ? department.getName() : null);
        }
        map.put("employeeId", register.getEmployeeId());
        map.put("physicianId", register.getEmployeeId());
        if (register.getEmployeeId() != null) {
            Employee employee = employeeMapper.selectById(register.getEmployeeId());
            map.put("physicianName", employee != null ? employee.getRealname() : null);
        }
        map.put("registLevelId", register.getRegistLevelId());
        if (register.getRegistLevelId() != null) {
            RegistLevel registLevel = registLevelMapper.selectById(register.getRegistLevelId());
            map.put("registLevelName", registLevel != null ? registLevel.getName() : null);
        }
        map.put("settleCategoryId", register.getSettleCategoryId());
        if (register.getSettleCategoryId() != null) {
            SettleCategory settleCategory = settleCategoryMapper.selectById(register.getSettleCategoryId());
            map.put("settleCategoryName", settleCategory != null ? settleCategory.getName() : null);
        }
        map.put("isBook", register.getIsBook());
        map.put("registMethod", register.getRegistMethod());
        map.put("complaint", null);
        map.put("registMoney", register.getRegistMoney());
        map.put("amount", register.getRegistMoney());
        fillPaymentStatus(map, register.getId());
        map.put("visitState", register.getVisitState());
        map.put("visitStateName", getStateName(register.getVisitState()));
        map.put("status", register.getVisitState());
        map.put("statusName", getStateName(register.getVisitState()));
        map.put("checkInTime", register.getCheckInTime());
        map.put("checkedIn", register.getCheckInTime() != null);
        return map;
    }

    private Map<String, Object> toExpenseRecordMap(ExpenseRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("registerId", record.getRegisterId());
        map.put("patientId", record.getPatientId());
        map.put("patientName", record.getPatientName());
        map.put("categoryId", record.getCategoryId());
        map.put("categoryName", record.getCategoryName());
        map.put("itemId", record.getItemId());
        map.put("itemName", record.getItemName());
        map.put("itemCode", record.getItemCode());
        map.put("quantity", record.getQuantity());
        map.put("unitPrice", record.getUnitPrice());
        map.put("totalAmount", record.getTotalAmount());
        map.put("status", record.getStatus());
        map.put("statusName", getExpenseStatusName(record.getStatus()));
        map.put("payTime", record.getPayTime());
        map.put("refundTime", record.getRefundTime());
        map.put("operatorName", record.getOperatorName());
        map.put("remark", record.getRemark());
        map.put("createTime", record.getCreateTime());
        return map;
    }

    private void fillPaymentStatus(Map<String, Object> map, Long registerId) {
        List<ExpenseRecord> expenseRecords = expenseRecordMapper.selectByRegisterId(registerId);
        if (expenseRecords == null || expenseRecords.isEmpty()) {
            map.put("payStatus", 0);
            map.put("payStatusName", "待缴费");
            return;
        }

        boolean hasRegistrationFee = expenseRecords.stream()
            .anyMatch(record -> "REGISTRATION_FEE".equals(record.getItemCode()));
        boolean hasPending = false;
        boolean hasPaid = false;
        boolean allRefunded = true;
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime latestPayTime = null;
        LocalDateTime latestRefundTime = null;
        for (ExpenseRecord record : expenseRecords) {
            if (hasRegistrationFee && !"REGISTRATION_FEE".equals(record.getItemCode())) {
                continue;
            }
            if (record.getStatus() == null || record.getStatus() == 0) {
                hasPending = true;
            }
            if (record.getStatus() != null && record.getStatus() == 1) {
                hasPaid = true;
            }
            if (record.getStatus() == null || record.getStatus() != 2) {
                allRefunded = false;
            }
            if (record.getTotalAmount() != null) {
                totalAmount = totalAmount.add(record.getTotalAmount());
            }
            if (record.getPayTime() != null && (latestPayTime == null || record.getPayTime().isAfter(latestPayTime))) {
                latestPayTime = record.getPayTime();
            }
            if (record.getRefundTime() != null && (latestRefundTime == null || record.getRefundTime().isAfter(latestRefundTime))) {
                latestRefundTime = record.getRefundTime();
            }
        }

        map.put("amount", totalAmount);
        map.put("payTime", latestPayTime);
        map.put("refundTime", latestRefundTime);
        if (hasPending) {
            map.put("payStatus", 0);
            map.put("payStatusName", "待缴费");
        } else if (hasPaid) {
            map.put("payStatus", 1);
            map.put("payStatusName", "已缴费");
        } else if (allRefunded) {
            map.put("payStatus", 2);
            map.put("payStatusName", "已退费");
        } else {
            map.put("payStatus", 0);
            map.put("payStatusName", "待缴费");
        }
    }

    private String getExpenseStatusName(Integer status) {
        return switch (status) {
            case 1 -> "已缴费";
            case 2 -> "已退费";
            case 3 -> "已作废";
            default -> "待缴费";
        };
    }

    private String getStateName(Integer state) {
        return switch (state) {
            case 1 -> "已挂号";
            case 2 -> "医生接诊";
            case 3 -> "看诊结束";
            case 4 -> "已退号";
            case 5 -> "检查检验中";
            case 6 -> "检查检验完成";
            case 7 -> "爽约";
            default -> "未知";
        };
    }
}
