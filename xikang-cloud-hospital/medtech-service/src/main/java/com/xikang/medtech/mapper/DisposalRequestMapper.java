package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.DisposalRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Disposal Request Mapper
 */
@Mapper
public interface DisposalRequestMapper {

    DisposalRequest selectById(Long id);

    List<DisposalRequest> selectByRegisterId(
        @Param("registerId") Long registerId,
        @Param("departmentId") Long departmentId
    );

    List<DisposalRequest> selectByDisposalState(
        @Param("disposalState") String disposalState,
        @Param("departmentId") Long departmentId
    );

    List<DisposalRequest> selectPending(@Param("departmentId") Long departmentId);

    int updateDisposalState(@Param("id") Long id, @Param("disposalState") String disposalState);

    int updateDisposalStateWithEmployee(
        @Param("id") Long id,
        @Param("disposalState") String disposalState,
        @Param("disposalEmployeeId") Long disposalEmployeeId
    );

    int updateResult(DisposalRequest disposalRequest);

    int updateArchive(
        @Param("id") Long id,
        @Param("disposalState") String disposalState,
        @Param("disposalRemark") String disposalRemark
    );
}
