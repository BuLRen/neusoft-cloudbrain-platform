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

    /**
     * P1-4.3 组合条件查询（AND）
     */
    List<DrugInfo> selectByConditions(@Param("keyword") String keyword,
                                      @Param("dosageForm") String dosageForm,
                                      @Param("category") String category);

    /**
     * P1-4.3 查询所有已用分类
     */
    List<String> selectCategories();

    List<DrugInfo> selectLowStock();

    DrugInfo selectByCode(String code);

    int insert(DrugInfo drugInfo);

    int update(DrugInfo drugInfo);

    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int deleteById(Long id);
}
