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

    /**
     * P1-4.2 查询近效期可用批次（status=1，失效日期 <= today + days）
     */
    List<DrugStock> selectExpiring(@Param("days") int days);

    int insert(DrugStock drugStock);

    int update(DrugStock drugStock);

    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
