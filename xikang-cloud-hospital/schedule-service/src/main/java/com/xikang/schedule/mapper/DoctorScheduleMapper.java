package com.xikang.schedule.mapper;

import com.xikang.schedule.entity.DoctorSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DoctorScheduleMapper {

    List<DoctorSchedule> selectAll();

    DoctorSchedule selectById(@Param("id") Long id);

    /**
     * 查询某科室某日期的可用排班（已发布状态的排班）
     */
    List<DoctorSchedule> selectAvailable(@Param("departmentId") Long departmentId,
                                         @Param("date") LocalDate date);

    /**
     * 查询某科室某日期范围的可用排班
     */
    List<DoctorSchedule> selectAvailableByDateRange(@Param("departmentId") Long departmentId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    /**
     * 查询某计划下某日期范围的排班
     */
    List<DoctorSchedule> selectByPlanIdAndDateRange(@Param("planId") Long planId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /**
     * 查询某排班计划下的所有排班
     */
    List<DoctorSchedule> selectByPlanId(@Param("planId") Long planId);

    /**
     * 查询某医生某日期的排班
     */
    DoctorSchedule selectByPhysicianAndDate(@Param("physicianId") Long physicianId,
                                            @Param("date") LocalDate date,
                                            @Param("timeSlot") String timeSlot);

    /**
     * 查询某医生的排班列表
     */
    List<DoctorSchedule> selectByPhysician(@Param("physicianId") Long physicianId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    int insert(DoctorSchedule schedule);

    int batchInsert(@Param("list") List<DoctorSchedule> schedules);

    int update(DoctorSchedule schedule);

    int updateQuota(@Param("id") Long id,
                   @Param("usedQuota") Integer usedQuota,
                   @Param("availableQuota") Integer availableQuota);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 排班调整时同步挂号表 register：换医生 + 写通知标记到 case_number 备注
     * <p>跨表写库（schedule-service 直接写 register 表），通过 scheduling_id 关联排班 ID。
     * <p>register 表无 remark 字段，所以把通知标记写到 modify_remark（如有）或
     * 通过 schedule.modify_remark 让前端 JOIN 显示。
     *
     * @param scheduleId        排班 ID（对应 register.scheduling_id）
     * @param oldPhysicianId    原医生 ID（双重保险，防止误改其他记录）
     * @param newPhysicianId    新医生 ID（对应 register.employee_id）
     * @param newPhysicianName  新医生姓名（保留参数，预留 register 表加姓名字段时用）
     * @param transferRemark    通知标记（暂存 schedule.modify_remark 由前端 JOIN）
     * @return 受影响行数
     */
    int updateRegistrationsPhysician(@Param("scheduleId") Long scheduleId,
                                     @Param("oldPhysicianId") Long oldPhysicianId,
                                     @Param("newPhysicianId") Long newPhysicianId,
                                     @Param("newPhysicianName") String newPhysicianName,
                                     @Param("transferRemark") String transferRemark);

    int deleteById(@Param("id") Long id);

    int deleteByPlanId(@Param("planId") Long planId);
}