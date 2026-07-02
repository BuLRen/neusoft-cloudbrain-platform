package com.xikang.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.*;
import com.xikang.registration.mapper.*;
import com.xikang.registration.feign.AuthPatientFeignClient;
import com.xikang.registration.feign.AiTriageFeignClient;
import com.xikang.registration.feign.PaymentFeignClient;
import com.xikang.registration.feign.ScheduleFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final AiTriageFeignClient aiTriageFeignClient;
    private final RefundService refundService;
    private final PaymentFeignClient paymentFeignClient;

    // 时段截止小时（24h）：超过这个时间，该时段未报到即视为爽约
    // 上午号 → 12:00 截止，下午号 → 18:00 截止
    // 系统无晚上号，兜底用 22:00
    private static final int NOON_DEADLINE_HOUR_AM = 12;
    private static final int NOON_DEADLINE_HOUR_PM = 18;
    private static final int NOON_DEADLINE_HOUR_FALLBACK = 22;

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

        // triageSessionId：前端从导诊页透传过来的导诊会话ID，用于精确回填 register_id 到本次导诊记录。
        // 若患者没做导诊直接挂号，此值为 null，回填会被跳过（预问诊走完整流程，不会污染）。
        String triageSessionId = request.get("triageSessionId") != null
            ? request.get("triageSessionId").toString() : null;

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

        Long maxRegisterId = registrationMapper.selectMaxId();
        Long nextSequenceValue = registrationMapper.selectNextSequenceValue();

        if (nextSequenceValue <= maxRegisterId) {
            Long syncedSequenceValue = registrationMapper.syncIdSequence();
            nextSequenceValue = syncedSequenceValue + 1;
            log.warn("register_id_seq 与表内最大 id 不一致，已自动同步 | maxId={}, syncedSeq={}", maxRegisterId, syncedSequenceValue);
        }

        registrationMapper.insert(register);

        ExpenseRecord registrationFee = createRegistrationFee(register, patientIdParam, realName, registLevel, registMoney, operatorId);
        Map<String, Object> payment = tryBalancePayment(patientIdParam, registrationFee, registMoney);
        // 清理同一挂号单上重复的待缴费 REGISTRATION_FEE，避免前端再次"去缴费"或"取消挂号"时把多条一起扣/退
        invalidateDuplicateRegistrationFees(register.getId(), registrationFee.getId());
        deductScheduleQuota(schedulingId, register.getId());

        log.info("挂号成功 | registerId={}, patient={}, department={}", register.getId(), realName, department.getName());

        // 挂号成功后按 sessionId 精确回填 register_id 到本次导诊记录（用于"导诊→预问诊"上下文串联）。
        // 【设计要点】
        //   1. 用 sessionId（前端从导诊页透传）精确匹配，替代旧的"按 patientId 猜最近一条"——
        //      后者曾把多次导诊/挂号交叉的记录错绑，导致预问诊读到错误的导诊内容。
        //   2. 放在 afterCommit 阶段：跨服务 Feign 调用慢且不稳，放在事务内会拖慢/回滚挂号主流程。
        //   3. triageSessionId 为 null（患者未做导诊）时跳过回填，预问诊自动走完整流程，不污染。
        //   4. 失败用 error 级日志暴露，让"导诊未关联"这类问题可见、可排查。
        if (triageSessionId != null && !triageSessionId.isBlank()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            final String sessionIdForBind = triageSessionId;
            final Long registerIdForBind = register.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        Map<String, Object> bindBody = new HashMap<>();
                        bindBody.put("sessionId", sessionIdForBind);
                        bindBody.put("registerId", registerIdForBind);
                        Map<String, Object> bindResp = aiTriageFeignClient.bindRegister(bindBody);
                        log.info("导诊记录回填完成 | sessionId={}, registerId={}, resp={}",
                                sessionIdForBind, registerIdForBind, bindResp);
                    } catch (Exception e) {
                        // 回填失败不影响已提交的挂号，但必须 error 级暴露：
                        // 这是"导诊→预问诊"链路断裂的根因，吞成 warn 会让人看不见。
                        log.error("导诊记录回填失败 | sessionId={}, registerId={}，预问诊将无法关联本次导诊",
                                sessionIdForBind, registerIdForBind, e);
                    }
                }
            });
        }

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
        // v3.2：改 Feign 调 payment-service.records（按 registerId 查所有费用行）
        try {
            Map<String, Object> resp = paymentFeignClient.records(null, id, null, null, null);
            Object dataObj = resp.get("data");
            if (dataObj instanceof List<?> list) {
                result.put("expenseRecords", list);
            } else {
                result.put("expenseRecords", List.of());
            }
        } catch (Exception e) {
            log.warn("getRegistration 调 payment.records 失败 | registerId={}, err={}", id, e.getMessage());
            result.put("expenseRecords", List.of());
        }
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
     * 获取当前登录用户管理的所有就诊人（本人+家属）的挂号列表。
     * 用于"我的挂号"页：本人 + 家属挂号统一展示。
     * 每条记录带 relation 字段（本人/配偶/父母等），便于前端区分标签。
     * 已退号(visit_state=4) 不展示。
     */
    public List<Map<String, Object>> listRegistrationsByManaged(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        List<Register> registers = registrationMapper.selectByManagedUserId(userId);
        return registers.stream().map(r -> {
            Map<String, Object> map = toMap(r);
            // relation 可能为空（数据补全前的老数据），统一兜底为"本人"
            String relation = r.getRelation();
            map.put("relation", relation != null ? relation : "本人");
            map.put("isFamily", relation != null && !"本人".equals(relation));
            return map;
        }).toList();
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

        // 关联字段：科室名 / 医生名 / 就诊日期 / 时段 / 挂号级别
        // 复用 toMap 私有方法，内部用 selectById 关联 department/employee/regist_level 表
        Map<String, Object> detail = toMap(register);
        result.put("departmentId", register.getDeptmentId());
        result.put("doctorId", register.getEmployeeId());
        result.put("departmentName", detail.get("departmentName"));
        result.put("doctorName", detail.get("physicianName"));
        result.put("visitDate", detail.get("visitDate"));
        result.put("noon", detail.get("noon"));
        result.put("registLevelName", detail.get("registLevelName"));

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

        // 时段未过校验：禁止挂"今天且已过截止时间"的号
        // 例：现在 2026-07-02 13:00，挂今天上午号 → 拒绝
        LocalDateTime deadline = computeMissDeadline(visitDate.atStartOfDay(), timeSlot);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(400, "该时段已过截止时间，无法挂号（" + timeSlot + " 截止于 " + deadline.toLocalTime() + "）");
        }
    }

    /**
     * 计算挂号记录的"爽约截止时刻"。
     * 业务规则：
     *   - 上午号：当天 12:00 之前未报到 → 爽约
     *   - 下午号：当天 18:00 之前未报到 → 爽约
     *   - 其他（兜底）：当天 22:00
     *
     * 公共方法，挂号校验、爽约定时任务、报到接口均复用本方法，
     * 保证判定口径一致。
     *
     * @param visitDate 挂号记录的 visit_date（实际存当天 00:00:00）
     * @param noon      时段字符串："上午" / "下午" / 其他
     */
    public static LocalDateTime computeMissDeadline(LocalDateTime visitDate, String noon) {
        int hour = switch (noon == null ? "" : noon) {
            case "上午" -> NOON_DEADLINE_HOUR_AM;
            case "下午" -> NOON_DEADLINE_HOUR_PM;
            default -> NOON_DEADLINE_HOUR_FALLBACK;
        };
        return visitDate.toLocalDate().atTime(hour, 0);
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
        if (response != null) {
            Object codeObj = response.get("code");
            if (codeObj instanceof Number num && num.intValue() != 200) {
                String msg = String.valueOf(response.getOrDefault("message", errorMessage));
                throw new BusinessException(num.intValue(), msg);
            }
        }
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
        // v3.2：改为 Feign 调 payment-service 创建 REGISTRATION_FEE 行（payment-service 端幂等）
        Map<String, Object> body = new HashMap<>();
        body.put("registerId", register.getId());
        body.put("patientId", patientId != null ? patientId.longValue() : null);
        body.put("patientName", patientName);
        body.put("categoryName", "挂号费");
        body.put("itemId", registLevel.getId());
        body.put("itemName", registLevel.getName() + "挂号费");
        body.put("itemCode", "REGISTRATION_FEE");
        body.put("quantity", 1);
        body.put("unitPrice", registMoney);
        body.put("amount", registMoney);
        body.put("operatorId", operatorId);
        body.put("operatorName", register.getRegistMethod());
        body.put("remark", "挂号自动生成费用");

        Map<String, Object> resp = paymentFeignClient.createItem(body);
        Map<String, Object> data = unwrapMapData(resp, "创建挂号费失败");
        Long itemId = toLong(data.get("itemId"));

        // 构造一个本地 ExpenseRecord 对象供后续 tryBalancePayment / invalidateDuplicateRegistrationFees 使用
        ExpenseRecord record = new ExpenseRecord();
        record.setId(itemId);
        record.setRegisterId(register.getId());
        record.setPatientId(patientId != null ? patientId.longValue() : null);
        record.setPatientName(patientName);
        record.setItemCode("REGISTRATION_FEE");
        record.setItemName(registLevel.getName() + "挂号费");
        record.setTotalAmount(registMoney);
        record.setStatus(0);
        return record;
    }

    private Map<String, Object> tryBalancePayment(Integer patientId, ExpenseRecord feeRecord, BigDecimal amount) {
        Map<String, Object> result = new HashMap<>();
        if (patientId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || feeRecord == null || feeRecord.getId() == null) {
            result.put("payStatus", 0);
            result.put("payStatusName", "待缴费");
            result.put("paymentMessage", "请到收费处缴费");
            return result;
        }

        // v3.2：改 Feign 调 payment-service.payItem，由 payment-service 完成扣款 + 写 expense_record + 回调本服务
        Map<String, Object> body = new HashMap<>();
        body.put("operatorId", patientId.longValue());
        body.put("operatorName", "患者余额");

        try {
            Map<String, Object> response = paymentFeignClient.payItem(feeRecord.getId(), body);
            Map<String, Object> data = unwrapMapData(response, "余额扣款失败");
            result.put("accountBalance", data.get("accountBalance"));
            result.put("payStatus", 1);
            result.put("payStatusName", "已缴费");
            result.put("paymentMessage", "余额支付成功");
            return result;
        } catch (Exception e) {
            // 扣款失败（余额不足等）：不抛异常，返回待缴费状态供前端引导
            log.warn("tryBalancePayment 失败 | registerId={}, itemId={}, err={}",
                    feeRecord.getRegisterId(), feeRecord.getId(), e.getMessage());
            result.put("payStatus", 0);
            result.put("payStatusName", "待缴费");
            result.put("paymentMessage", e.getMessage() != null ? e.getMessage() : "余额不足，请充值后缴费");
            return result;
        }
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
     *
     * v3.2：payment-service 端 createItem 已用 "先 SELECT 后 INSERT" 保证一个挂号至多一条 REGISTRATION_FEE，
     * 重复行再也不会出现，本方法保留为 no-op 以兼容既有调用点（createRegistration L232）。
     */
    private void invalidateDuplicateRegistrationFees(Long registerId, Long keepId) {
        // v3.2：no-op（payment-service 端 createItem 幂等保证）
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

    /**
     * v3.2 §4.2 回调入口：payment-service.payItem 成功后通过 Feign 调本方法。
     * 重算 payment.summary，按聚合结果更新 register.pay_status；
     * 若 visit_state==1（已挂号未接诊）且费用全部付清，推进到 2（医生接诊）。
     * 幂等：重复调用只会得到相同的最终态。
     */
    @Transactional
    public Map<String, Object> onFeePaid(Long registerId) {
        log.info("on-fee-paid 回调 | registerId={}", registerId);
        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);

        // 重算 summary
        Integer payStatus = null;
        try {
            Map<String, Object> resp = paymentFeignClient.summary(registerId);
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> m && m.get("payStatus") != null) {
                payStatus = ((Number) m.get("payStatus")).intValue();
            }
        } catch (Exception e) {
            log.warn("on-fee-paid 回调重算 summary 失败 | registerId={}, err={}", registerId, e.getMessage());
        }

        if (payStatus != null) {
            registrationMapper.updatePayStatus(registerId, payStatus);
            // 仅在 visit_state==1 且全部付清时推进到接诊
            if (payStatus == 1) {
                Register register = registrationMapper.selectById(registerId);
                if (register != null && register.getVisitState() != null && register.getVisitState() == 1) {
                    registrationMapper.updateStatus(registerId, 2);
                    log.info("on-fee-paid 推进就诊状态 1→2 | registerId={}", registerId);
                }
            }
            result.put("payStatus", payStatus);
        }
        result.put("success", true);
        return result;
    }

    private void fillPaymentStatus(Map<String, Object> map, Long registerId) {
        // v3.2：改 Feign 调 payment-service.summary，由 payment-service 汇总
        try {
            Map<String, Object> response = paymentFeignClient.summary(registerId);
            Map<String, Object> data = unwrapMapData(response, "查询支付状态失败");
            map.put("amount", data.get("amount"));
            map.put("payTime", data.get("payTime"));
            map.put("refundTime", data.get("refundTime"));
            map.put("payStatus", data.get("payStatus"));
            map.put("payStatusName", data.get("payStatusName"));
        } catch (Exception e) {
            log.warn("fillPaymentStatus 调 payment.summary 失败，回退为待缴费 | registerId={}, err={}",
                    registerId, e.getMessage());
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
