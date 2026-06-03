package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.Scheduling;
import com.xikang.registration.mapper.SchedulingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduling Service - 排班规则服务
 * 注意：scheduling 表仅存储排班规则（rule_name, week_rule），不存储具体号源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SchedulingMapper schedulingMapper;

    /**
     * 创建排班规则
     */
    @Transactional
    public Map<String, Object> createScheduling(Map<String, Object> schedulingData) {
        log.info("创建排班规则: {}", schedulingData);

        String ruleName = (String) schedulingData.get("ruleName");
        String weekRule = (String) schedulingData.get("weekRule");

        Scheduling scheduling = new Scheduling();
        scheduling.setRuleName(ruleName);
        scheduling.setWeekRule(weekRule);

        schedulingMapper.insert(scheduling);
        log.info("排班规则创建成功: id={}", scheduling.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", scheduling.getId());
        result.put("ruleName", ruleName);
        result.put("weekRule", weekRule);

        return result;
    }

    /**
     * 更新排班规则
     */
    @Transactional
    public void updateScheduling(Long id, Map<String, Object> schedulingData) {
        log.info("更新排班规则: id={}, data={}", id, schedulingData);

        Scheduling existing = schedulingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "排班不存在");
        }

        if (schedulingData.containsKey("ruleName")) {
            existing.setRuleName((String) schedulingData.get("ruleName"));
        }
        if (schedulingData.containsKey("weekRule")) {
            existing.setWeekRule((String) schedulingData.get("weekRule"));
        }

        schedulingMapper.update(existing);
        log.info("排班规则更新成功: id={}", id);
    }

    /**
     * 删除排班规则
     */
    @Transactional
    public void deleteScheduling(Long id) {
        log.info("删除排班规则: id={}", id);

        Scheduling existing = schedulingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "排班不存在");
        }

        schedulingMapper.deleteById(id);
        log.info("排班规则已删除: id={}", id);
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
     * 按科室查询排班列表
     */
    public List<Map<String, Object>> getSchedulingByDepartment(Long departmentId) {
        List<Scheduling> schedulings = schedulingMapper.selectByDepartmentId(departmentId);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按日期查询所有排班
     */
    public List<Map<String, Object>> getSchedulingByDate(java.time.LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectByDate(date);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按科室和日期查询可用排班
     */
    public List<Map<String, Object>> getAvailableScheduling(Long departmentId, java.time.LocalDate date) {
        List<Scheduling> schedulings = schedulingMapper.selectAvailableByDepartmentAndDate(departmentId, date);
        return schedulings.stream().map(this::toMap).toList();
    }

    /**
     * 按医生和日期范围查询排班（scheduling表不存储医生信息，此方法暂不支持）
     */
    public List<Map<String, Object>> getSchedulingByPhysicianAndDateRange(
            Long physicianId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        return List.of();
    }

    /**
     * 获取号源使用情况（scheduling表不存储号源，此方法暂不支持）
     */
    public Map<String, Object> getQuotaUsage(Long id) {
        return Map.of("id", id, "message", "排班规则表不存储号源信息");
    }

    /**
     * 批量创建排班规则
     */
    @Transactional
    public List<Map<String, Object>> batchCreateScheduling(List<Map<String, Object>> schedulingList) {
        log.info("批量创建排班规则: count={}", schedulingList.size());

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (Map<String, Object> schedulingData : schedulingList) {
            try {
                Map<String, Object> result = createScheduling(schedulingData);
                results.add(result);
            } catch (Exception e) {
                log.error("创建排班规则失败: {}", schedulingData, e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                errorResult.put("data", schedulingData);
                results.add(errorResult);
            }
        }

        return results;
    }

    private Map<String, Object> toMap(Scheduling scheduling) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", scheduling.getId());
        map.put("ruleName", scheduling.getRuleName());
        map.put("weekRule", scheduling.getWeekRule());
        map.put("delmark", scheduling.getDelmark());
        return map;
    }
}