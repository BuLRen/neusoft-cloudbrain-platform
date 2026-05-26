package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.DrugStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Drug Stock Mapper
 */
@Mapper
public interface DrugStockMapper {

    DrugStock selectById(Long id);

    List<DrugStock> selectByDrugId(Long drugId);

    List<DrugStock> selectByDrugIdAndStatus(Long drugId);

    int insert(DrugStock drugStock);

    int update(DrugStock drugStock);

    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);
}
