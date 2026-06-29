package com.xikang.physician.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PhysicianMapper {

    List<Map<String, Object>> selectPatients(
        @Param("keyword") String keyword,
        @Param("employeeId") Long employeeId,
        @Param("offset") int offset,
        @Param("size") int size
    );

    long countPatients(@Param("keyword") String keyword, @Param("employeeId") Long employeeId);

    Map<String, Object> selectPatientStats(@Param("employeeId") Long employeeId);

    Map<String, Object> selectHistoricalSummary(@Param("employeeId") Long employeeId);

    Map<String, Object> selectMedicalRecordByRegisterId(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiseasesByMedicalRecordId(@Param("medicalRecordId") Long medicalRecordId);

    int insertMedicalRecord(Map<String, Object> record);

    int updateMedicalRecord(Map<String, Object> record);

    int updateMedicalRecordPreliminary(Map<String, Object> record);

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

    Map<String, Object> selectRegisterById(@Param("registerId") Long registerId);

    Long selectRegisterEmployeeId(@Param("registerId") Long registerId);

    Long selectRegisterIdByMedicalRecordId(@Param("medicalRecordId") Long medicalRecordId);

    Map<String, Object> selectPatientByRegisterId(@Param("registerId") Long registerId);

    long countPendingExamRequests(@Param("registerId") Long registerId);

    int updateVisitState(@Param("registerId") Long registerId, @Param("visitState") int visitState);

    Map<String, Object> selectLatestAiConsultation(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectAvailableExaminations();

    List<Map<String, Object>> selectOpenRequestsForSimulation(@Param("registerId") Long registerId);

    int deleteExamSuggestionsByRegisterId(@Param("registerId") Long registerId);

    int insertExamSuggestion(Map<String, Object> row);

    int updateCheckRequestResult(
        @Param("registerId") Long registerId,
        @Param("techId") Long techId,
        @Param("result") String result,
        @Param("state") String state
    );

    int updateInspectionRequestResult(
        @Param("registerId") Long registerId,
        @Param("techId") Long techId,
        @Param("result") String result,
        @Param("state") String state
    );

    int deleteExamAnalysisByRegisterId(@Param("registerId") Long registerId);

    int insertExamAnalysis(Map<String, Object> row);

    List<Map<String, Object>> selectExamAnalysisByRegisterId(@Param("registerId") Long registerId);

    int deleteDiagnosisSuggestionsByRegisterId(@Param("registerId") Long registerId);

    int insertDiagnosisSuggestion(Map<String, Object> row);

    int deleteDrugSuggestionsByRegisterId(@Param("registerId") Long registerId);

    int insertDrugSuggestion(Map<String, Object> row);

    List<Map<String, Object>> selectDrugSuggestions(@Param("registerId") Long registerId);

    int updateDrugSuggestionAdopted(@Param("id") Long id, @Param("isAdopted") int isAdopted);

    int insertAiMedicalRecordLog(Map<String, Object> row);

    Map<String, Object> selectLatestAiMedicalRecordLogBySourceType(
        @Param("registerId") Long registerId,
        @Param("sourceType") String sourceType
    );
}
