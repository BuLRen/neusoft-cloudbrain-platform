package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SimulationContextMapper {

    Map<String, Object> selectMedicalRecordByRegisterId(@Param("registerId") Long registerId);

    Map<String, Object> selectLatestAiConsultationByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseasesByRegisterId(@Param("registerId") Long registerId);
}
