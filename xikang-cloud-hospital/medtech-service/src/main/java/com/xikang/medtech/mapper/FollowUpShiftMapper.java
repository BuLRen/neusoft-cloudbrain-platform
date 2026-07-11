package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpShiftMapper {

    Map<String, Object> selectPlanByDeptMonth(
        @Param("departmentId") Long departmentId,
        @Param("month") String month
    );

    Map<String, Object> selectPlanById(@Param("planId") Long planId);

    int insertPlan(Map<String, Object> payload);

    int updatePlanStatus(
        @Param("planId") Long planId,
        @Param("status") String status
    );

    int deleteShiftsByPlanId(@Param("planId") Long planId);

    int deleteContactTasksByPlanId(@Param("planId") Long planId);

    int deleteChangeRequestsByPlanId(@Param("planId") Long planId);

    int insertStaffShift(Map<String, Object> payload);

    int insertContactTask(Map<String, Object> payload);

    List<Map<String, Object>> selectStaffShifts(
        @Param("employeeId") Long employeeId,
        @Param("departmentId") Long departmentId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    List<Map<String, Object>> selectContactTasksByShiftId(@Param("shiftId") Long shiftId);

    List<Map<String, Object>> selectContactTasksByShiftIds(@Param("shiftIds") List<Long> shiftIds);

    Map<String, Object> selectShiftByEmployeeAndDate(
        @Param("employeeId") Long employeeId,
        @Param("workDate") LocalDate workDate
    );

    Map<String, Object> selectShiftById(@Param("shiftId") Long shiftId);

    int updateShiftDate(
        @Param("shiftId") Long shiftId,
        @Param("workDate") LocalDate workDate
    );

    List<Map<String, Object>> selectFollowUpStaffByDepartment(@Param("departmentId") Long departmentId);

    List<Map<String, Object>> selectPatientsForShiftPlanning(@Param("departmentId") Long departmentId);

    int insertChangeRequest(Map<String, Object> payload);

    List<Map<String, Object>> selectPendingChangeRequests(@Param("departmentId") Long departmentId);

    Map<String, Object> selectChangeRequestById(@Param("id") Long id);

    int updateChangeRequestStatus(Map<String, Object> payload);

    int countPendingChangeRequests(@Param("departmentId") Long departmentId);

    Map<String, Object> selectShiftInPlanByEmployeeAndDate(
        @Param("planId") Long planId,
        @Param("employeeId") Long employeeId,
        @Param("workDate") LocalDate workDate
    );

    Map<String, Object> selectContactTaskByRegisterAndWorkDate(
        @Param("registerId") Long registerId,
        @Param("workDate") LocalDate workDate
    );

    List<Map<String, Object>> selectShiftsWithTaskCounts(
        @Param("departmentId") Long departmentId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    int countContactTasksByShiftId(@Param("shiftId") Long shiftId);

    List<Map<String, Object>> selectContactTasksByPlanId(@Param("planId") Long planId);
}
