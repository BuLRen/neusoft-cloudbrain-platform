package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.CriticalValueAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CriticalValueAlertMapper {

    int insert(CriticalValueAlert alert);

    CriticalValueAlert selectById(@Param("id") Long id);

    List<CriticalValueAlert> selectPendingByDoctorId(@Param("doctorId") Long doctorId);

    List<CriticalValueAlert> selectOverduePending(@Param("now") LocalDateTime now);

    List<CriticalValueAlert> selectBoardAlerts(@Param("limit") int limit);

    int updateAcknowledged(
        @Param("id") Long id,
        @Param("acknowledgedTime") LocalDateTime acknowledgedTime,
        @Param("status") String status
    );

    int updateHandled(
        @Param("id") Long id,
        @Param("handledTime") LocalDateTime handledTime,
        @Param("handleNote") String handleNote,
        @Param("status") String status
    );

    int updateEscalated(
        @Param("id") Long id,
        @Param("escalatedTime") LocalDateTime escalatedTime,
        @Param("status") String status
    );

    List<Map<String, Object>> selectByRegisterId(@Param("registerId") Long registerId);

    Map<String, Object> selectBoardStats();
}
