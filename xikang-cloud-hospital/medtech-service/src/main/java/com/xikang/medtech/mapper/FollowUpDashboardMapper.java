package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpDashboardMapper {

    List<Map<String, Object>> selectDashboardPatients(
        @Param("departmentId") Long departmentId,
        @Param("targetDate") LocalDate targetDate,
        @Param("includeUnenrolledEligible") boolean includeUnenrolledEligible,
        @Param("demoOnlyProfile") boolean demoOnlyProfile
    );

    List<Map<String, Object>> selectTrackedDates(
        @Param("registerId") Long registerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    List<Map<String, Object>> selectDaySchedules(
        @Param("departmentId") Long departmentId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    Map<String, Object> selectDayScheduleById(@Param("id") Long id);

    Map<String, Object> selectExistingInterviewSchedule(
        @Param("registerId") Long registerId,
        @Param("scheduleDate") LocalDate scheduleDate
    );

    int insertDaySchedule(Map<String, Object> payload);

    int updateDayScheduleStatus(
        @Param("id") Long id,
        @Param("status") String status
    );

    Map<String, Object> selectDailyObservation(
        @Param("registerId") Long registerId,
        @Param("observationDate") LocalDate observationDate
    );

    int insertDailyObservation(Map<String, Object> payload);

    Map<String, Object> selectDashboardStats(
        @Param("departmentId") Long departmentId,
        @Param("targetDate") LocalDate targetDate,
        @Param("includeUnenrolledEligible") boolean includeUnenrolledEligible,
        @Param("demoOnlyProfile") boolean demoOnlyProfile
    );

    Map<String, Object> selectEmployeeBrief(@Param("employeeId") Long employeeId);

    Long selectRegisterDepartmentId(@Param("registerId") Long registerId);

    boolean isEligiblePatient(@Param("registerId") Long registerId);

    int upsertPatientProfile(Map<String, Object> payload);

    int upsertEnrollment(Map<String, Object> payload);

    Map<String, Object> selectEnrollmentByRegisterId(@Param("registerId") Long registerId);

    List<Long> selectEligibleRegisterIdsNotEnrolled(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    int countEligibleRegisterIdsNotEnrolled();

    int claimMonitoring(@Param("registerId") Long registerId, @Param("employeeId") Long employeeId);

    int claimMonitoringProfile(@Param("registerId") Long registerId, @Param("employeeId") Long employeeId);

    int releaseMonitoring(
        @Param("registerId") Long registerId,
        @Param("employeeId") Long employeeId,
        @Param("forceRelease") boolean forceRelease
    );

    int releaseMonitoringProfile(
        @Param("registerId") Long registerId,
        @Param("employeeId") Long employeeId,
        @Param("forceRelease") boolean forceRelease
    );

    Map<String, Object> selectMonitoringByRegisterId(@Param("registerId") Long registerId);
}
