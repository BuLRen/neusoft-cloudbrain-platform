package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpPatientMapper {

    List<Map<String, Object>> selectPlansByRegisterIds(@Param("registerIds") List<Long> registerIds);

    List<Map<String, Object>> selectRecordsByRegisterIds(@Param("registerIds") List<Long> registerIds);

    List<Map<String, Object>> selectPrescriptionsByRegisterIds(@Param("registerIds") List<Long> registerIds);

    int updatePlanStatus(@Param("id") Long id, @Param("planStatus") String planStatus);

    int insertFollowUpRecord(Map<String, Object> payload);

    List<Long> selectRegisterIdsByPatientMatch(
        @Param("realName") String realName,
        @Param("birthdate") String birthdate
    );

    List<Long> selectRegisterIdsByPatientId(@Param("patientId") Long patientId);
}
