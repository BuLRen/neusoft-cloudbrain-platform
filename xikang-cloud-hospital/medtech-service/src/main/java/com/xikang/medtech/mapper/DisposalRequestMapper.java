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

    List<DisposalRequest> selectByRegisterId(Long registerId);

    List<DisposalRequest> selectByPatientId(Long patientId);

    List<DisposalRequest> selectByStatus(Integer status);

    List<DisposalRequest> selectPending();

    int insert(DisposalRequest disposalRequest);

    int update(DisposalRequest disposalRequest);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateResult(@Param("id") Long id, @Param("result") String result);
}
