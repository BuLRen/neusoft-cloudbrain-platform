package com.xikang.ai.catalog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DiseaseCatalogMapper {

    List<Map<String, Object>> searchDiseasesForAi(
        @Param("diseaseKeywords") List<String> diseaseKeywords,
        @Param("icdPrefixes") List<String> icdPrefixes,
        @Param("categoryKeywords") List<String> categoryKeywords,
        @Param("fetchLimit") int fetchLimit
    );

    List<Map<String, Object>> searchDiseasesByNameKeyword(
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );

    List<Map<String, Object>> searchDiseasesByIcdPrefix(
        @Param("prefix") String prefix,
        @Param("limit") int limit
    );

    List<Map<String, Object>> searchDiseasesByCategoryKeyword(
        @Param("keyword") String keyword,
        @Param("limit") int limit
    );
}
