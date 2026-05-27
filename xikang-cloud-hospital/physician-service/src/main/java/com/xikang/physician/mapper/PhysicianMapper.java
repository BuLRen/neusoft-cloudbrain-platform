package com.xikang.physician.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PhysicianMapper {

    List<Map<String, Object>> selectPatients(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    long countPatients(@Param("keyword") String keyword);

    Map<String, Object> selectPatientStats();

    Map<String, Object> selectMedicalRecordByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseasesByMedicalRecordId(@Param("medicalRecordId") Long medicalRecordId);

    int insertMedicalRecord(Map<String, Object> record);

    int updateMedicalRecord(Map<String, Object> record);

    int updateDiagnosis(Map<String, Object> diagnosis);

    int deleteMedicalRecordDiseases(@Param("medicalRecordId") Long medicalRecordId);

    int insertMedicalRecordDisease(@Param("medicalRecordId") Long medicalRecordId, @Param("diseaseId") Long diseaseId);

    List<Map<String, Object>> selectMedicalTechnologies(@Param("techType") String techType, @Param("keyword") String keyword);

    int insertCheckRequest(Map<String, Object> item);

    int insertInspectionRequest(Map<String, Object> item);

    int insertDisposalRequest(Map<String, Object> item);

    List<Map<String, Object>> selectCheckResults(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectInspectionResults(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseases(@Param("keyword") String keyword);

    List<Map<String, Object>> selectDrugs(@Param("keyword") String keyword);

    Map<String, Object> selectDrugById(@Param("id") Long id);

    int insertPrescription(Map<String, Object> item);

    List<Map<String, Object>> selectPrescriptions(@Param("registerId") Long registerId);

    int deletePrescription(@Param("id") Long id);

    List<Map<String, Object>> selectExamSuggestions(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiagnosisSuggestions(@Param("registerId") Long registerId);

    Map<String, Object> selectLatestPrescriptionReview(@Param("registerId") Long registerId);
}
