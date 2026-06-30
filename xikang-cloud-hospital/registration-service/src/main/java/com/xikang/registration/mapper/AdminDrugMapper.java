package com.xikang.registration.mapper;

import com.xikang.registration.dto.DrugCatalogView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminDrugMapper {

    long countCatalog(
        @Param("keyword") String keyword,
        @Param("dosageForm") String dosageForm,
        @Param("category") String category
    );

    List<DrugCatalogView> selectCatalog(
        @Param("keyword") String keyword,
        @Param("dosageForm") String dosageForm,
        @Param("category") String category,
        @Param("offset") int offset,
        @Param("size") int size
    );

    List<String> selectCategories();
}
