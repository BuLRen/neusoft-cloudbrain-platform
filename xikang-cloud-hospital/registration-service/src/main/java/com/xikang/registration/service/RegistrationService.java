package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.*;
import com.xikang.registration.mapper.*;
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

    /**
     * 创建挂号记录
     */
    @Transactional
    public Map<String, Object> createRegistration(Map<String, Object> request) {
        log.info("创建挂号 | request={}", request);

        Long patientId = ((Number) request.get("patientId")).longValue();
        String patientName = (String) request.get("patientName");
        String patientPhone = (String) request.get("patientPhone");
        String idCard = (String) request.get("idCard");
        Long departmentId = ((Number) request.get("departmentId")).longValue();
        Long physicianId = request.get("physicianId") != null
            ? ((Number) request.get("physicianId")).longValue()
            : null;
        Long schedulingId = request.get("schedulingId") != null
            ? ((Number) request.get("schedulingId")).longValue()
            : null;
        String visitTime = (String) request.get("visitTime");
        String complaint = (String) request.get("complaint");
        Integer registerType = (Integer) request.getOrDefault("registerType", 0);
        Long operatorId = request.get("operatorId") != null
            ? ((Number) request.get("operatorId")).longValue()
            : null;
        String operatorName = (String) request.get("operatorName");
        String aiTriageResult = request.get("aiTriageResult") != null
            ? request.get("aiTriageResult").toString()
            : null;

        // 获取科室信息
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BusinessException(400, "科室不存在");
        }

        // 计算挂号费用
        BigDecimal amount = BigDecimal.ZERO;
        String registLevelName = "普通";
        Long registLevelId = null;

        if (registerType == 1) { // 专家号
            List<RegistLevel> levels = registLevelMapper.selectAll();
            RegistLevel expertLevel = levels.stream()
                .filter(l -> l.getName().contains("专家"))
                .findFirst()
                .orElse(null);
            if (expertLevel != null) {
                amount = expertLevel.getPrice();
                registLevelName = expertLevel.getName();
                registLevelId = expertLevel.getId();
            }
        }

        // 如果选择了排班，校验号源
        Integer usedQuota = 0;
        if (schedulingId != null) {
            Scheduling scheduling = schedulingMapper.selectById(schedulingId);
            if (scheduling == null) {
                throw new BusinessException(400, "排班不存在");
            }
            if (scheduling.getUsedQuota() >= scheduling.getTotalQuota()) {
                throw new BusinessException(400, "该排班号源已满");
            }
            usedQuota = scheduling.getUsedQuota() + 1;
            schedulingMapper.updateUsedQuota(schedulingId, usedQuota);

            if (physicianId == null) {
                physicianId = scheduling.getPhysicianId();
            }
        }

        // 获取医生姓名
        String physicianName = (String) request.get("physicianName");
        if (physicianId != null && physicianName == null) {
            physicianName = request.get("physicianName") != null
                ? (String) request.get("physicianName")
                : "待分配";
        }

        // 构建挂号记录
        Register register = new Register();
        register.setPatientId(patientId);
        register.setPatientName(patientName);
        register.setPatientPhone(patientPhone);
        register.setIdCard(idCard);
        register.setDepartmentId(departmentId);
        register.setDepartmentName(department.getName());
        register.setPhysicianId(physicianId);
        register.setPhysicianName(physicianName);
        register.setSchedulingId(schedulingId);
        register.setVisitDate(LocalDate.now());
        register.setVisitTime(visitTime);
        register.setComplaint(complaint);
        register.setStatus(0); // 待缴费
        register.setRegisterType(registerType);
        register.setRegistLevelId(registLevelId);
        register.setRegistLevelName(registLevelName);
        register.setAmount(amount);
        register.setPayStatus(0); // 待支付
        register.setAiTriageResult(aiTriageResult);
        register.setOperatorId(operatorId);
        register.setOperatorName(operatorName);
        register.setCreateTime(LocalDateTime.now());

        registrationMapper.insert(register);

        log.info("挂号成功 | registerId={}, patientId={}, department={}", register.getId(), patientId, department.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("id", register.getId());
        result.put("patientName", patientName);
        result.put("departmentName", department.getName());
        result.put("physicianName", physicianName);
        result.put("visitDate", register.getVisitDate());
        result.put("visitTime", visitTime);
        result.put("amount", amount);
        result.put("status", 0);
        result.put("statusName", "待缴费");

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

        // 校验状态：只有待缴费或已缴费未接诊的可以取消
        if (register.getStatus() > 1) {
            throw new BusinessException(400, "该挂号状态不允许取消");
        }

        // 更新状态
        registrationMapper.updateStatus(id, 4); // 已取消

        // 如果有排班，释放号源
        if (register.getSchedulingId() != null) {
            Scheduling scheduling = schedulingMapper.selectById(register.getSchedulingId());
            if (scheduling != null && scheduling.getUsedQuota() > 0) {
                schedulingMapper.updateUsedQuota(register.getSchedulingId(), scheduling.getUsedQuota() - 1);
            }
        }

        log.info("退号成功 | registerId={}", id);
    }

    /**
     * 获取排班可用号源
     */
    public Map<String, Object> getSchedulingAvailable(Long schedulingId) {
        Scheduling scheduling = schedulingMapper.selectById(schedulingId);
        if (scheduling == null) {
            throw new BusinessException(404, "排班不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", scheduling.getId());
        result.put("physicianName", scheduling.getPhysicianName());
        result.put("departmentName", scheduling.getDepartmentName());
        result.put("workDate", scheduling.getWorkDate());
        result.put("timeSlot", scheduling.getTimeSlot());
        result.put("totalQuota", scheduling.getTotalQuota());
        result.put("usedQuota", scheduling.getUsedQuota());
        result.put("availableQuota", scheduling.getTotalQuota() - scheduling.getUsedQuota());

        return result;
    }

    /**
     * 获取科室的可用排班
     */
    public List<Map<String, Object>> getAvailableScheduling(Long departmentId, LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectAvailableByDepartmentAndDate(departmentId, date);
        return schedulings.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("physicianId", s.getPhysicianId());
            map.put("physicianName", s.getPhysicianName());
            map.put("departmentId", s.getDepartmentId());
            map.put("departmentName", s.getDepartmentName());
            map.put("workDate", s.getWorkDate());
            map.put("timeSlot", s.getTimeSlot());
            map.put("totalQuota", s.getTotalQuota());
            map.put("usedQuota", s.getUsedQuota());
            map.put("availableQuota", s.getTotalQuota() - s.getUsedQuota());
            return map;
        }).toList();
    }

    /**
     * 获取挂号级别列表
     */
    public List<RegistLevel> getRegistLevels() {
        return registLevelMapper.selectAll();
    }

    private Map<String, Object> toMap(Register register) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", register.getId());
        map.put("patientId", register.getPatientId());
        map.put("patientName", register.getPatientName());
        map.put("patientPhone", register.getPatientPhone());
        map.put("idCard", register.getIdCard());
        map.put("departmentId", register.getDepartmentId());
        map.put("departmentName", register.getDepartmentName());
        map.put("physicianId", register.getPhysicianId());
        map.put("physicianName", register.getPhysicianName());
        map.put("visitDate", register.getVisitDate());
        map.put("visitTime", register.getVisitTime());
        map.put("complaint", register.getComplaint());
        map.put("status", register.getStatus());
        map.put("statusName", getStatusName(register.getStatus()));
        map.put("registerType", register.getRegisterType());
        map.put("registerTypeName", register.getRegisterType() == 0 ? "普通" : "专家");
        map.put("registLevelName", register.getRegistLevelName());
        map.put("amount", register.getAmount());
        map.put("payStatus", register.getPayStatus());
        map.put("payStatusName", getPayStatusName(register.getPayStatus()));
        map.put("aiTriageResult", register.getAiTriageResult());
        map.put("aiPreVisit", register.getAiPreVisit());
        map.put("createTime", register.getCreateTime());
        return map;
    }

    private String getStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待缴费";
            case 1 -> "已缴费";
            case 2 -> "已接诊";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private String getPayStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已退款";
            default -> "未知";
        };
    }
}
