package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpClinicalMapper {

    Map<String, Object> selectMedicalRecordByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectPrescriptionsByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseasesByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> searchDrugs(@Param("keyword") String keyword, @Param("limit") int limit);

    int upsertLastVisitSnapshot(Map<String, Object> payload);
}
