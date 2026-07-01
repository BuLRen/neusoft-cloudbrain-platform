package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RevisitRequestMapper {

    int insertRequest(Map<String, Object> payload);

    List<Map<String, Object>> selectByRegisterId(
        @Param("registerId") Long registerId,
        @Param("status") String status
    );

    List<Map<String, Object>> selectPending(@Param("departmentId") Long departmentId);

    int countPendingByRegisterId(@Param("registerId") Long registerId);
}
