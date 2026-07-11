package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpMedtechExamReadMapper {

    List<Map<String, Object>> selectExamMetricCatalog();

    List<Map<String, Object>> selectInspectionExamRows(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectCheckExamRows(@Param("registerId") Long registerId);
}
