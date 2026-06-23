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

    int deleteById(@Param("id") Long id);

    int deleteByPlanId(@Param("planId") Long planId);
}