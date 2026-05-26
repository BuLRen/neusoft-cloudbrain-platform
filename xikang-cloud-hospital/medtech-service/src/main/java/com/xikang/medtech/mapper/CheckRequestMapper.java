package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.CheckRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Check Request Mapper
 */
@Mapper
public interface CheckRequestMapper {

    CheckRequest selectById(Long id);

    List<CheckRequest> selectByRegisterId(Long registerId);

    List<CheckRequest> selectByPatientId(Long patientId);

    List<CheckRequest> selectByStatus(Integer status);

    List<CheckRequest> selectPending();

    int insert(CheckRequest checkRequest);

    int update(CheckRequest checkRequest);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateResult(@Param("id") Long id, @Param("result") String result, @Param("aiAnalysis") String aiAnalysis);
}
