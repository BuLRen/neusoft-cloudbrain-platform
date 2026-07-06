package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.MedicalTechnology;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Medical Technology Mapper
 */
@Mapper
public interface MedicalTechnologyMapper {

    MedicalTechnology selectById(Long id);

    List<MedicalTechnology> selectList(
        @Param("techType") String techType,
        @Param("keyword") String keyword,
        @Param("offset") Integer offset,
        @Param("size") Integer size
    );

    long countList(
        @Param("techType") String techType,
        @Param("keyword") String keyword
    );

    MedicalTechnology selectByTechCode(
        @Param("techCode") String techCode,
        @Param("excludeId") Long excludeId
    );

    int countReferences(Long id);

    int deleteAiExamSuggestionsByTechId(Long id);

    int insert(MedicalTechnology medicalTechnology);

    int update(MedicalTechnology medicalTechnology);

    int deleteById(Long id);

    List<Map<String, Object>> selectDepartmentOptions();
}
