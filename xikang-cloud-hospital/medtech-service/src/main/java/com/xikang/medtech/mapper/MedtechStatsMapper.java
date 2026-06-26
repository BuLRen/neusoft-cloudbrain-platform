package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface MedtechStatsMapper {

    Map<String, Object> selectHistoricalSummary(@Param("departmentId") Long departmentId);
}
