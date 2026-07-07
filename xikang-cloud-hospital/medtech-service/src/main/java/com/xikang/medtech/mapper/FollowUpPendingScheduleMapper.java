package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface FollowUpPendingScheduleMapper {

    int insertPending(Map<String, Object> payload);

    Map<String, Object> selectByRegisterAndWorkDate(
        @Param("registerId") Long registerId,
        @Param("workDate") java.time.LocalDate workDate
    );
}
