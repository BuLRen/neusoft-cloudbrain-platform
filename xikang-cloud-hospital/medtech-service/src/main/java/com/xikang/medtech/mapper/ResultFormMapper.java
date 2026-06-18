package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.ResultFormCategory;
import com.xikang.medtech.entity.ResultFormField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResultFormMapper {

    List<ResultFormCategory> selectAllCategories();

    ResultFormCategory selectCategoryByCode(String categoryCode);

    List<ResultFormField> selectFieldsByOwner(@Param("ownerType") String ownerType, @Param("ownerKey") String ownerKey);

    int deleteFieldsByOwner(@Param("ownerType") String ownerType, @Param("ownerKey") String ownerKey);

    int insertField(ResultFormField field);
}
