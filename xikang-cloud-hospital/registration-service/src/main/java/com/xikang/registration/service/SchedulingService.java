package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.Scheduling;
import com.xikang.registration.mapper.SchedulingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduling Service - 医生排班服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SchedulingMapper schedulingMapper;

    /**
     * 创建排班
     */
    @Transactional
    public Map<String, Object> createScheduling(Map<String, Object> schedulingData) {
        log.info("创建排班: {}", schedulingData);

        Long physicianId = ((Number) schedulingData.get("physicianId")).longValue();
        String physicianName = (String) schedulingData.get("physicianName");
        Long departmentId = ((Number) schedulingData.get("departmentId")).longValue();
        String departmentName = (String) schedulingData.get("departmentName");
        LocalDate workDate = LocalDate.parse((CharSequence) schedulingData.get("workDate"));
        String timeSlot = (String) schedulingData.get("timeSlot");
        Integer totalQuota = (Integer) schedulingData.getOrDefault("totalQuota", 20);
        String remark = (String) schedulingData.getOrDefault("remark", "");

        // 检查是否已有相同排班
        List<Scheduling> existing = schedulingMapper.selectByDepartmentAndDate(departmentId, workDate);
        boolean conflict = existing.stream()
            .anyMatch(s -> s.getTimeSlot().equals(timeSlot) && s.getPhysicianId().equals(physicianId));
        if (conflict) {
            throw new BusinessException(400, "该时间段已有排班");
        }

        Scheduling scheduling = new Scheduling();
        scheduling.setPhysicianId(physicianId);
        scheduling.setPhysicianName(physicianName);
        scheduling.setDepartmentId(departmentId);
        scheduling.setDepartmentName(departmentName);
        scheduling.setWorkDate(workDate);
        scheduling.setTimeSlot(timeSlot);
        scheduling.setTotalQuota(totalQuota);
        scheduling.setUsedQuota(0);
        scheduling.setStatus(1);
        scheduling.setRemark(remark);
        scheduling.setCreateTime(LocalDateTime.now());

        schedulingMapper.insert(scheduling);

        log.info("排班创建成功: id={}", scheduling.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", scheduling.getId());
        result.put("physicianName", physicianName);
        result.put("departmentName", departmentName);
        result.put("workDate", workDate);
        result.put("timeSlot", timeSlot);
        result.put("totalQuota", totalQuota);
        result.put("status", 1);

        return result;
    }

    /**
     * 更新排班
     */
    @Transactional
    public void updateScheduling(Long id, Map<String, Object> schedulingData) {
        log.info("更新排班: id={}, data={}", id, schedulingData);

        Scheduling existing = schedulingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "排班不存在");
        }

        // 如果已使用的号源大于新的总数，不允许修改
        Integer newTotalQuota = (Integer) schedulingData.getOrDefault("totalQuota", existing.getTotalQuota());
        if (existing.getUsedQuota() > newTotalQuota) {
            throw new BusinessException(400, "已使用号源大于设置总数，无法修改");
        }

        existing.setTotalQuota(newTotalQuota);
        existing.setRemark((String) schedulingData.getOrDefault("remark", existing.getRemark()));

        if (schedulingData.containsKey("status")) {
            existing.setStatus((Integer) schedulingData.get("status"));
        }

        schedulingMapper.update(existing);
        log.info("排班更新成功: id={}", id);
    }

    /**
     * 删除排班（停诊）
     */
    @Transactional
    public void deleteScheduling(Long id) {
        log.info("删除排班: id={}", id);

        Scheduling existing = schedulingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "排班不存在");
        }

        if (existing.getUsedQuota() > 0) {
            throw new BusinessException(400, "已有患者挂号，无法删除");
        }

        schedulingMapper.deleteById(id);
        log.info("排班已停诊: id={}", id);
    }

    /**
     * 获取排班详情
     */
    public Map<String, Object> getSchedulingDetail(Long id) {
        Scheduling scheduling = schedulingMapper.selectById(id);
        if (scheduling == null) {
            throw new BusinessException(404, "排班不存在");
        }
        return toMap(scheduling);
    }

    /**
     * 按科室和日期查询可用排班
     */
    public List<Map<String, Object>> getAvailableScheduling(Long departmentId, LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectAvailableByDepartmentAndDate(departmentId, date);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按科室查询排班列表
     */
    public List<Map<String, Object>> getSchedulingByDepartment(Long departmentId) {
        List<Scheduling> schedulings = schedulingMapper.selectByDepartmentId(departmentId);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按日期查询所有排班
     */
    public List<Map<String, Object>> getSchedulingByDate(LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectByDate(date);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按医生和日期范围查询排班
     */
    public List<Map<String, Object>> getSchedulingByPhysicianAndDateRange(
            Long physicianId, LocalDate startDate, LocalDate endDate) {
        List<Scheduling> schedulings = schedulingMapper.selectByPhysicianAndDateRange(physicianId, startDate, endDate);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 批量创建排班
     */
    @Transactional
    public List<Map<String, Object>> batchCreateScheduling(List<Map<String, Object>> schedulingList) {
        log.info("批量创建排班: count={}", schedulingList.size());

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (Map<String, Object> schedulingData : schedulingList) {
            try {
                Map<String, Object> result = createScheduling(schedulingData);
                results.add(result);
            } catch (Exception e) {
                log.error("创建排班失败: {}", schedulingData, e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                errorResult.put("data", schedulingData);
                results.add(errorResult);
            }
        }

        return results;
    }

    /**
     * 获取号源使用情况
     */
    public Map<String, Object> getQuotaUsage(Long id) {
        Scheduling scheduling = schedulingMapper.selectById(id);
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
        result.put("usageRate", scheduling.getTotalQuota() > 0
            ? (double) scheduling.getUsedQuota() / scheduling.getTotalQuota() * 100
            : 0);

        return result;
    }

    private Map<String, Object> toMap(Scheduling scheduling) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", scheduling.getId());
        map.put("physicianId", scheduling.getPhysicianId());
        map.put("physicianName", scheduling.getPhysicianName());
        map.put("departmentId", scheduling.getDepartmentId());
        map.put("departmentName", scheduling.getDepartmentName());
        map.put("workDate", scheduling.getWorkDate());
        map.put("timeSlot", scheduling.getTimeSlot());
        map.put("timeSlotName", getTimeSlotName(scheduling.getTimeSlot()));
        map.put("totalQuota", scheduling.getTotalQuota());
        map.put("usedQuota", scheduling.getUsedQuota());
        map.put("availableQuota", scheduling.getTotalQuota() - scheduling.getUsedQuota());
        map.put("status", scheduling.getStatus());
        map.put("statusName", scheduling.getStatus() == 1 ? "可挂号" : "已停诊");
        map.put("remark", scheduling.getRemark());
        map.put("createTime", scheduling.getCreateTime());
        return map;
    }

    private String getTimeSlotName(String timeSlot) {
        return switch (timeSlot) {
            case "morning" -> "上午";
            case "afternoon" -> "下午";
            case "evening" -> "夜班";
            case "all_day" -> "全天";
            default -> timeSlot;
        };
    }
}
