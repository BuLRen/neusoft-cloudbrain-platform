package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PatientFollowUpAuthMapper {

    List<Long> selectPatientIdsByUserId(@Param("userId") Long userId);

    boolean isRegisterAccessible(
        @Param("registerId") Long registerId,
        @Param("patientIds") List<Long> patientIds,
        @Param("userId") Long userId
    );
}
