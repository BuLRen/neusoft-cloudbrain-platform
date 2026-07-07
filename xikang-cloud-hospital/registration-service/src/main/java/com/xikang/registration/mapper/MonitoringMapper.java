package com.xikang.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MonitoringMapper {

    List<Map<String, Object>> selectHighRiskTriageTimeouts();

    int countPendingTriage();

    List<Map<String, Object>> selectDepartmentWaitingBacklog();

    List<Map<String, Object>> selectLowStockDrugs(@Param("limit") int limit);
}
