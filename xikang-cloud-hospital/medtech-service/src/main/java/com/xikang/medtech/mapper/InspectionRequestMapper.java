package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.InspectionRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Inspection Request Mapper
 */
@Mapper
public interface InspectionRequestMapper {

    InspectionRequest selectById(Long id);

    List<InspectionRequest> selectByRegisterId(Long registerId);

    List<InspectionRequest> selectByPatientId(Long patientId);

    List<InspectionRequest> selectByStatus(Integer status);

    List<InspectionRequest> selectPending();

    int insert(InspectionRequest inspectionRequest);

    int update(InspectionRequest inspectionRequest);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateResult(@Param("id") Long id, @Param("result") String result, @Param("aiAnalysis") String aiAnalysis);

    int updateSpecimenTime(@Param("id") Long id, @Param("specimenTime") java.time.LocalDateTime specimenTime);
}
