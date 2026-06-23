package com.xikang.registration.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ClinicalRecordMapper {

    Map<String, Object> selectRegisterHeader(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectVisitsByPatient(@Param("patientId") Long patientId);

    Map<String, Object> selectLatestPreConsultation(@Param("registerId") Long registerId);

    Map<String, Object> selectAiTriageByRegister(@Param("registerId") Long registerId);

    Map<String, Object> selectMedicalRecord(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseasesByMedicalRecordId(@Param("medicalRecordId") Long medicalRecordId);

    List<Map<String, Object>> selectCheckRequests(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectInspectionRequests(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDisposalRequests(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectPrescriptions(@Param("registerId") Long registerId);

    int countUserPatientAccess(@Param("userId") Long userId, @Param("patientId") Long patientId);

    Map<String, Object> selectPatientClinicalProfile(@Param("patientId") Long patientId);

    Map<String, Object> selectPatientBasic(@Param("patientId") Long patientId);

    int upsertPatientClinicalProfile(Map<String, Object> profile);

    int archiveRegister(@Param("registerId") Long registerId, @Param("employeeId") Long employeeId);

    String selectLatestCaseNumberByPatient(@Param("patientId") Long patientId);
}
