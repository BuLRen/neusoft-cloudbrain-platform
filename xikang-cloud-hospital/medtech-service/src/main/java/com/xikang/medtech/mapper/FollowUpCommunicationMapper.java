package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpCommunicationMapper {

    List<Map<String, Object>> selectSessions(@Param("departmentId") Long departmentId);

    Map<String, Object> selectSessionById(@Param("id") Long id);

    Map<String, Object> selectSessionByRegisterId(@Param("registerId") Long registerId);

    int insertSession(Map<String, Object> payload);

    int updateSessionDepartment(@Param("id") Long id, @Param("departmentId") Long departmentId);

    int updateSessionDoctorActive(@Param("id") Long id);

    int updateAiEscalation(@Param("id") Long id, @Param("enabled") int enabled);

    List<Map<String, Object>> selectMessages(
        @Param("sessionId") Long sessionId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    int countMessages(@Param("sessionId") Long sessionId);

    int insertMessage(Map<String, Object> payload);

    Map<String, Object> selectLatestCaseSummary(@Param("registerId") Long registerId);

    Map<String, Object> selectSharedCaseSummary(@Param("registerId") Long registerId);

    Map<String, Object> selectCaseSummaryById(@Param("id") Long id);

    int insertCaseSummary(Map<String, Object> payload);

    int updateCaseSummaryDoctorContent(
        @Param("id") Long id,
        @Param("doctorContent") String doctorContent
    );

    int approveCaseSummary(Map<String, Object> payload);

    int revokeCaseSummary(@Param("id") Long id);

    List<Map<String, Object>> selectRecentFollowUpRecords(
        @Param("registerId") Long registerId,
        @Param("limit") int limit
    );

    Map<String, Object> selectTodayObservation(
        @Param("registerId") Long registerId,
        @Param("observationDate") LocalDate observationDate
    );

    Map<String, Object> selectTodayInterview(
        @Param("registerId") Long registerId,
        @Param("scheduleDate") LocalDate scheduleDate
    );

    Map<String, Object> selectPatientBriefProfile(@Param("registerId") Long registerId);

    int countDoctorUnreadTotal(@Param("departmentId") Long departmentId);

    List<Map<String, Object>> selectDoctorUnreadByRegister(@Param("departmentId") Long departmentId);

    int countPatientUnread(@Param("registerId") Long registerId);

    Long selectLatestMessageId(@Param("sessionId") Long sessionId);

    int markDoctorRead(@Param("sessionId") Long sessionId, @Param("messageId") Long messageId);

    int markPatientRead(@Param("registerId") Long registerId, @Param("messageId") Long messageId);
}
