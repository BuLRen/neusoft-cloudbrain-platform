package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpCommunicationMapper {

    List<Map<String, Object>> selectSessions(@Param("departmentId") Long departmentId);

    Map<String, Object> selectSessionById(@Param("id") Long id);

    Map<String, Object> selectSessionByRegisterId(@Param("registerId") Long registerId);

    int insertSession(Map<String, Object> payload);

    int updateSessionDoctorActive(@Param("id") Long id, @Param("activeAt") LocalDateTime activeAt);

    int updateSessionAiEscalation(@Param("id") Long id, @Param("enabled") int enabled);

    List<Map<String, Object>> selectMessages(
        @Param("sessionId") Long sessionId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    int countMessages(@Param("sessionId") Long sessionId);

    int insertMessage(Map<String, Object> payload);

    Map<String, Object> selectLatestCaseSummary(@Param("registerId") Long registerId);

    Map<String, Object> selectCaseSummaryById(@Param("id") Long id);

    int insertCaseSummary(Map<String, Object> payload);

    int updateCaseSummaryDraft(Map<String, Object> payload);

    int updateCaseSummaryApproved(Map<String, Object> payload);

    int revokeCaseSummary(@Param("id") Long id);

    Map<String, Object> selectRegisterDepartmentId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectRecentMetrics(
        @Param("registerId") Long registerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    List<Map<String, Object>> selectRecentFollowUpRecords(@Param("registerId") Long registerId, @Param("limit") int limit);

    Map<String, Object> selectTodayObservation(@Param("registerId") Long registerId, @Param("date") LocalDate date);

    Map<String, Object> selectTodayInterview(@Param("registerId") Long registerId, @Param("date") LocalDate date);
}
