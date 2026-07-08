package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpPendingScheduleMapper {

    int insertPending(Map<String, Object> payload);

    Map<String, Object> selectByRegisterAndWorkDate(
        @Param("registerId") Long registerId,
        @Param("workDate") LocalDate workDate
    );

    List<Map<String, Object>> selectPendingByDepartment(
        @Param("departmentId") Long departmentId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("status") String status
    );

    int markApplied(@Param("id") Long id);
}
