package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpOutcomeMapper {

    List<Map<String, Object>> selectFollowUpPatients(
        @Param("visitState") Integer visitState,
        @Param("departmentId") Long departmentId
    );

    Map<String, Object> selectPatientProfile(@Param("registerId") Long registerId);

    Map<String, Object> selectPatientDetail(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectPatientDiseases(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectHealthMetrics(
        @Param("registerId") Long registerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("metricKeys") List<String> metricKeys
    );

    List<Map<String, Object>> selectFollowUpRecords(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectInterviewSchedules(
        @Param("weekStart") LocalDate weekStart,
        @Param("status") String status
    );

    Map<String, Object> selectInterviewScheduleByRegisterAndWeek(
        @Param("registerId") Long registerId,
        @Param("weekStartDate") LocalDate weekStartDate
    );

    int insertInterviewSchedule(Map<String, Object> payload);
}
