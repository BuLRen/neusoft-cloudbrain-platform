package com.xikang.schedule.service;

import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.ScheduleAdjustLog;
import com.xikang.schedule.entity.ScheduleAdjustRequest;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
import com.xikang.schedule.mapper.ScheduleAdjustLogMapper;
import com.xikang.schedule.mapper.ScheduleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAdjustService {

    private final ScheduleAdjustRequestMapper adjustRequestMapper;
    private final ScheduleAdjustLogMapper adjustLogMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;

    /**
     * 获取所有待确认调整
     */
    public List<ScheduleAdjustRequest> getPendingAdjusts() {
        return adjustRequestMapper.selectPending();
    }

    /**
     * 获取调整详情
     */
    public ScheduleAdjustRequest getById(Long id) {
        return adjustRequestMapper.selectById(id);
    }

    /**
     * 获取某排班的所有调整申请
     */
    public List<ScheduleAdjustRequest> getByScheduleId(Long scheduleId) {
        return adjustRequestMapper.selectByScheduleId(scheduleId);
    }

    /**
     * 创建调整申请
     */
    @Transactional
    public ScheduleAdjustRequest createRequest(ScheduleAdjustRequest request) {
        request.setStatus("待确认");
        adjustRequestMapper.insert(request);

        //记录日志
        logAdjust(request.getScheduleId(), "adjust_request", null,
                request.getAdjustType(), request.getAdjustType(),
                request.getTriggeredBy(), request.getReason());

        log.info("创建调整申请：排班ID={}, 类型={}, 触发人={}",
                request.getScheduleId(), request.getAdjustType(), request.getTriggeredBy());

        return request;
    }

    /**
     * 确认调整
     */
    @Transactional
    public void confirmAdjust(Long requestId, Long confirmedBy, String remark) {
        ScheduleAdjustRequest request = adjustRequestMapper.selectById(requestId);
        if (request == null) {
            throw new RuntimeException("调整申请不存在");
        }

        // 更新申请状态
        adjustRequestMapper.updateStatus(requestId, "已确认",
                confirmedBy, LocalDateTime.now(), remark);

        // 执行调整
        executeAdjust(request);

        log.info("确认调整：申请ID={}, 确认人={}", requestId, confirmedBy);
    }

    /**
     * 驳回调整
     */
    @Transactional
    public void rejectAdjust(Long requestId, Long rejectedBy, String reason) {
        adjustRequestMapper.updateStatus(requestId, "已驳回",
                rejectedBy, LocalDateTime.now(), reason);

        log.info("驳回调整：申请ID={}, 驳回人={}, 原因={}", requestId, rejectedBy, reason);
    }

    /**
     * 执行调整
     */
    private void executeAdjust(ScheduleAdjustRequest request) {
        DoctorSchedule schedule = doctorScheduleMapper.selectById(request.getScheduleId());
        if (schedule == null) {
            throw new RuntimeException("排班不存在");
        }

        // 记录原值
        Long oldPhysicianId = schedule.getPhysicianId();
        String oldStatus = schedule.getStatus();
        Integer oldQuota = schedule.getTotalQuota();

        // 执行调整
        if (request.getNewPhysicianId() != null && !request.getNewPhysicianId().equals(oldPhysicianId)) {
            schedule.setPhysicianId(request.getNewPhysicianId());
            schedule.setStatus("替班");
            logAdjust(request.getScheduleId(), "physician_id",
                    String.valueOf(oldPhysicianId), String.valueOf(request.getNewPhysicianId()),
                    request.getAdjustType(), request.getTriggeredBy(), request.getReason());
        }

        if (request.getNewStatus() != null && !request.getNewStatus().equals(oldStatus)) {
            schedule.setStatus(request.getNewStatus());
            logAdjust(request.getScheduleId(), "status", oldStatus, request.getNewStatus(),
                    request.getAdjustType(), request.getTriggeredBy(), request.getReason());
        }

        if (request.getNewQuota() != null && !request.getNewQuota().equals(oldQuota)) {
            schedule.setTotalQuota(request.getNewQuota());
            schedule.setAvailableQuota(request.getNewQuota() - schedule.getUsedQuota());
            logAdjust(request.getScheduleId(), "total_quota",
                    String.valueOf(oldQuota), String.valueOf(request.getNewQuota()),
                    request.getAdjustType(), request.getTriggeredBy(), request.getReason());
        }

        schedule.setModified(true);
        schedule.setModifyRemark(request.getAiSuggestion());
        doctorScheduleMapper.update(schedule);

        log.info("执行调整完成：排班ID={}", request.getScheduleId());
    }

    /**
     * 获取调整日志
     */
    public List<ScheduleAdjustLog> getAdjustLogs(Long scheduleId) {
        return adjustLogMapper.selectByScheduleId(scheduleId);
    }

    /**
     * 记录调整日志
     */
    private void logAdjust(Long scheduleId, String fieldName, String oldValue,
                          String newValue, String adjustType, Long adjustBy, String remark) {
        ScheduleAdjustLog adjustLog = new ScheduleAdjustLog();
        adjustLog.setScheduleId(scheduleId);
        adjustLog.setFieldName(fieldName);
        adjustLog.setOldValue(oldValue);
        adjustLog.setNewValue(newValue);
        adjustLog.setAdjustType(adjustType);
        adjustLog.setAdjustBy(adjustBy);
        adjustLog.setRemark(remark);
        adjustLogMapper.insert(adjustLog);
    }
}