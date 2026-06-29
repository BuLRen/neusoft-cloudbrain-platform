package com.xikang.ai.catalog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DrugCatalogMapper {

    List<Map<String, Object>> searchDrugsForAi(
        @Param("drugKeywords") List<String> drugKeywords,
        @Param("genericKeywords") List<String> genericKeywords,
        @Param("categoryKeywords") List<String> categoryKeywords,
        @Param("indicationKeywords") List<String> indicationKeywords,
        @Param("fetchLimit") int fetchLimit
    );
}
