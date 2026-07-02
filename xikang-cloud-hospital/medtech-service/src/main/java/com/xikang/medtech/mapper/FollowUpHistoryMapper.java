package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpHistoryMapper {

    int insertEvent(Map<String, Object> payload);

    List<Map<String, Object>> selectEvents(
        @Param("registerId") Long registerId,
        @Param("departmentId") Long departmentId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("eventType") String eventType,
        @Param("limit") Integer limit
    );

    List<Map<String, Object>> selectRecentPatientFeedback(
        @Param("registerId") Long registerId,
        @Param("limit") int limit
    );

    Long selectRegisterDepartmentId(@Param("registerId") Long registerId);
}
