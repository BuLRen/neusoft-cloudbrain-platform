package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface FollowUpVisitReportMapper {

    Map<String, Object> selectLatestByRegisterId(@Param("registerId") Long registerId);

    Map<String, Object> selectById(@Param("id") Long id);

    int insertReport(Map<String, Object> payload);

    int updateReport(Map<String, Object> payload);

    int finalizeReport(@Param("id") Long id, @Param("generatedBy") Long generatedBy);
}
