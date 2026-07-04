package com.xikang.schedule.service;

import com.xikang.schedule.client.NotificationClient;
import com.xikang.schedule.dto.NotificationSendRequest;
import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.ScheduleAdjustLog;
import com.xikang.schedule.entity.ScheduleAdjustRequest;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
import com.xikang.schedule.mapper.ScheduleAdjustLogMapper;
import com.xikang.schedule.mapper.ScheduleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAdjustService {

    private final ScheduleAdjustRequestMapper adjustRequestMapper;
    private final ScheduleAdjustLogMapper adjustLogMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;
    private final NotificationClient notificationClient;

    /** 通知服务内部调用鉴权 token */
    @Value("${notification.internal-token:notif-internal-2026}")
    private String notificationInternalToken;

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
     * 驳回并重新生成 AI 方案
     * <p>把原调整申请标记为「已驳回」，然后调用 LeaveRequestService.processLeave 重新跑 Dify 工作流。
     * <p>需要 LeaveRequestService 注入（避免循环依赖，用 ApplicationContext 懒加载）。
     */
    @Transactional
    public ScheduleAdjustRequest regenAdjust(Long requestId, Long operatorId, String reason) {
        ScheduleAdjustRequest old = adjustRequestMapper.selectById(requestId);
        if (old == null) {
            throw new RuntimeException("调整申请不存在");
        }

        // 标记原申请为「已驳回」（数据库 chk_sar_status 约束只允许 待确认/已确认/已驳回 三个值，
        // 不能写「已驳回-重新生成」等组合状态，故用 confirm_remark 区分语义）。
        String remark = String.format("[重新生成] %s", reason != null ? reason : "管理员要求重新推荐");
        adjustRequestMapper.updateStatus(requestId, "已驳回",
                operatorId, LocalDateTime.now(), remark);
        log.info("驳回并重新生成：申请ID={}, 操作人={}, reason={}", requestId, operatorId, reason);

        return old;
    }

    /**
     * 执行调整
     * <p>1. 修改 doctor_schedule 表（医生/状态/号源）
     * <p>2. 修改对应挂号记录的 physicianId + physicianName + 写通知标记到 remark
     * <p>注意：挂号表 registration 跨服务，这里通过同库直写实现（registration-service 也用同一 PostgreSQL）
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
            // 唯一约束预检：替班医生当天同时段不能已有班次（idx_ds_unique = work_date+physician_id+time_slot）
            // 否则 UPDATE 会撞唯一约束报 500。提前给出业务可读的错误。
            DoctorSchedule conflict = doctorScheduleMapper.selectByPhysicianAndDate(
                    request.getNewPhysicianId(), schedule.getWorkDate(), schedule.getTimeSlot());
            if (conflict != null) {
                String newName = request.getSubstitutePhysicianName() != null
                        ? request.getSubstitutePhysicianName()
                        : "医生" + request.getNewPhysicianId();
                log.warn("替班冲突：医生 {} 在 {} {} 已有班次（scheduleId={}），无法替换 scheduleId={}",
                        request.getNewPhysicianId(), schedule.getWorkDate(), schedule.getTimeSlot(),
                        conflict.getId(), request.getScheduleId());
                throw new RuntimeException(String.format(
                        "替班医生 %s 在 %s %s 已有班次，无法直接替换。建议改用停诊或换其他替班医生。",
                        newName, schedule.getWorkDate(), schedule.getTimeSlot()));
            }

            schedule.setPhysicianId(request.getNewPhysicianId());
            schedule.setStatus("替班");
            logAdjust(request.getScheduleId(), "physician_id",
                    String.valueOf(oldPhysicianId), String.valueOf(request.getNewPhysicianId()),
                    request.getAdjustType(), request.getTriggeredBy(), request.getReason());

            // 同步挂号表：把对应 schedule 的所有挂号记录改医生 + 写通知标记
            String oldPhysicianName = schedule.getPhysicianName() != null
                    ? schedule.getPhysicianName() : "原医生" + oldPhysicianId;
            String newPhysicianName = request.getSubstitutePhysicianName() != null
                    ? request.getSubstitutePhysicianName()
                    : "替班医生" + request.getNewPhysicianId();
            updateRegistrationsForPhysicianChange(
                    request.getScheduleId(),
                    oldPhysicianId, oldPhysicianName,
                    request.getNewPhysicianId(), newPhysicianName,
                    request.getId());
            // schedule.physicianName 也要更新（方便后续展示）
            schedule.setPhysicianName(newPhysicianName);
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
        schedule.setModifyRemark("已调整：" + (request.getReason() != null ? request.getReason() : "管理员调整"));
        doctorScheduleMapper.update(schedule);

        // 仅当医生确实变更时才发"医生变更"通知（仅改号源/状态不发，避免无意义消息）
        // 顶层再加一道 try-catch：即使 sendDoctorChangeNotifications 内部某条 catch 失误抛出，
        // 也不会回滚排班调整事务（消息是辅助，不能阻塞主业务）。
        if (request.getNewPhysicianId() != null
                && !request.getNewPhysicianId().equals(oldPhysicianId)) {
            try {
                sendDoctorChangeNotifications(request, schedule,
                        oldPhysicianId, oldPhysicianNameSafe(schedule, oldPhysicianId),
                        request.getNewPhysicianId(),
                        schedule.getPhysicianName() != null ? schedule.getPhysicianName() : ("替班医生" + request.getNewPhysicianId()));
            } catch (Exception ex) {
                log.warn("发送医生变更通知整体失败（不阻塞调整）：scheduleId={}, err={}",
                        schedule.getId(), ex.getMessage());
            }
        }

        log.info("执行调整完成：排班ID={}", request.getScheduleId());
    }

    /**
     * 发送"医生变更"通知：1 条给替班医生 + N 条给受影响患者
     * <p>关键约束：通知失败不能回滚排班调整事务。所有 Feign 调用包在 try-catch 里，仅 log warn。
     */
    private void sendDoctorChangeNotifications(ScheduleAdjustRequest request,
                                               DoctorSchedule schedule,
                                               Long oldPhysicianId, String oldPhysicianName,
                                               Long newPhysicianId, String newPhysicianName) {
        String dateStr = schedule.getWorkDate() != null ? schedule.getWorkDate().toString() : "未知日期";
        String slot = schedule.getTimeSlot() != null ? schedule.getTimeSlot() : "未知时段";
        String reason = request.getReason() != null ? request.getReason() : "管理员调整";

        // 1. 通知替班医生（你被指派为新接手的医生）
        try {
            NotificationSendRequest toSub = new NotificationSendRequest(
                    newPhysicianId, "physician", "doctor_change",
                    "您被指派为替班医生",
                    String.format("排班 #%d（%s %s）原医生 %s → 您，原因：%s",
                            schedule.getId(), dateStr, slot, oldPhysicianName, reason),
                    "schedule_adjust_request", request.getId());
            notificationClient.send(toSub, notificationInternalToken);
            log.info("已通知替班医生：physicianId={}, scheduleId={}",
                    newPhysicianId, schedule.getId());
        } catch (Exception ex) {
            log.warn("通知替班医生失败（不阻塞调整）：physicianId={}, err={}",
                    newPhysicianId, ex.getMessage());
        }

        // 2. 通知所有受影响患者（批量）
        try {
            List<Long> patientIds = doctorScheduleMapper.selectPatientIdsBySchedule(schedule.getId());
            if (patientIds == null || patientIds.isEmpty()) {
                log.info("无受影响患者，跳过批量通知：scheduleId={}", schedule.getId());
                return;
            }
            List<NotificationSendRequest> batch = new ArrayList<>(patientIds.size());
            String patientContent = String.format(
                    "您在 %s %s 的就诊医生已由 %s 医生变更为 %s 医生，请按原时间就诊。",
                    dateStr, slot, oldPhysicianName, newPhysicianName);
            for (Long patientId : patientIds) {
                batch.add(new NotificationSendRequest(
                        patientId, "patient", "doctor_change",
                        "您的就诊医生已变更",
                        patientContent,
                        "schedule_adjust_request", request.getId()));
            }
            notificationClient.batchSend(batch, notificationInternalToken);
            log.info("已通知受影响患者：scheduleId={}, count={}",
                    schedule.getId(), batch.size());
        } catch (Exception ex) {
            log.warn("通知受影响患者失败（不阻塞调整）：scheduleId={}, err={}",
                    schedule.getId(), ex.getMessage());
        }
    }

    private String oldPhysicianNameSafe(DoctorSchedule schedule, Long oldPhysicianId) {
        return schedule.getPhysicianName() != null
                ? schedule.getPhysicianName()
                : ("原医生" + oldPhysicianId);
    }

    /**
     * 同步更新挂号记录：换医生 + 写通知标记到 remark 字段
     * <p>挂号表 registration 在同一 PostgreSQL 库，直接写库避免跨服务调用复杂度。
     * <p>标记格式："[医生变更] 原医生 X → 新医生 Y，原因：Z（调整单 #N）"
     */
    private void updateRegistrationsForPhysicianChange(Long scheduleId,
                                                       Long oldPhysicianId, String oldPhysicianName,
                                                       Long newPhysicianId, String newPhysicianName,
                                                       Long adjustRequestId) {
        try {
            String transferRemark = String.format("[医生变更] 原医生 %s → %s（调整单 #%d）",
                    oldPhysicianName, newPhysicianName, adjustRequestId);
            int affected = doctorScheduleMapper.updateRegistrationsPhysician(
                    scheduleId, oldPhysicianId, newPhysicianId, newPhysicianName, transferRemark);
            log.info("同步挂号记录换医生：scheduleId={}, 影响行数={}", scheduleId, affected);
        } catch (Exception e) {
            // 挂号表更新失败不应阻塞排班调整
            log.error("同步挂号表换医生失败（不阻塞调整）：scheduleId={}, err={}",
                    scheduleId, e.getMessage());
        }
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