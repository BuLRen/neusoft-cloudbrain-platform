package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpMonitoringMapper {

    int assignMonitoring(@Param("registerId") Long registerId, @Param("employeeId") Long employeeId);

    int assignMonitoringProfile(@Param("registerId") Long registerId, @Param("employeeId") Long employeeId);

    int insertTransferRequest(Map<String, Object> payload);

    List<Map<String, Object>> selectPendingTransferRequests(@Param("departmentId") Long departmentId);

    Map<String, Object> selectTransferRequestById(@Param("id") Long id);

    int updateTransferRequestStatus(Map<String, Object> payload);

    int countPendingTransferRequests(@Param("departmentId") Long departmentId);
}
