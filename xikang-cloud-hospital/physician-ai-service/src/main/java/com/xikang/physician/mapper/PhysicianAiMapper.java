package com.xikang.physician.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PhysicianAiMapper {

    List<Map<String, Object>> selectExamSuggestions(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectDiagnosisSuggestions(@Param("registerId") Long registerId);

    int deleteExamSuggestionsByRegisterId(@Param("registerId") Long registerId);

    int insertExamSuggestion(Map<String, Object> row);

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
