package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpContactMapper {

    int insertContactRecord(Map<String, Object> payload);

    List<Map<String, Object>> selectContactRecords(
        @Param("registerId") Long registerId,
        @Param("limit") Integer limit
    );

    Map<String, Object> selectContactRecordById(@Param("id") Long id);

    int completeShiftContactTask(
        @Param("shiftId") Long shiftId,
        @Param("registerId") Long registerId
    );
}
