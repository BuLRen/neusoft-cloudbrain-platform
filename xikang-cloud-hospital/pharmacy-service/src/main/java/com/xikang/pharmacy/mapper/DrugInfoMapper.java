package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.DrugInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Drug Info Mapper
 */
@Mapper
public interface DrugInfoMapper {

    DrugInfo selectById(Long id);

    List<DrugInfo> selectAll();

    List<DrugInfo> selectByKeyword(String keyword);

    List<DrugInfo> selectByDosageForm(String dosageForm);

    List<DrugInfo> selectLowStock();

    DrugInfo selectByCode(String code);

    int insert(DrugInfo drugInfo);

    int update(DrugInfo drugInfo);

    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int deleteById(Long id);
}
