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

    List<CheckRequest> selectByCheckState(String checkState);

    List<CheckRequest> selectPending();

    int updateCheckState(@Param("id") Long id, @Param("checkState") String checkState);

    int updateCheckStateWithEmployee(
        @Param("id") Long id,
        @Param("checkState") String checkState,
        @Param("checkEmployeeId") Long checkEmployeeId
    );

    int updateResult(CheckRequest checkRequest);
}
