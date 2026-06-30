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
     * 查询近效期可用批次（status=1，失效日期 <= today + days）。
     * 16 万+ 批次，裸查可能命中很多，老调用保留 LIMIT 200 兜底。
     */
    List<DrugStock> selectExpiring(@Param("days") int days);

    /** 近效期批次计数 */
    long countExpiring(@Param("days") int days);

    /** 近效期批次分页 */
    List<DrugStock> selectExpiringPage(@Param("days") int days,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int insert(DrugStock drugStock);

    int update(DrugStock drugStock);

    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
