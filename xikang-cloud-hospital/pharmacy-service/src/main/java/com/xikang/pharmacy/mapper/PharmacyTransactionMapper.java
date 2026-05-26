package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.PharmacyTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pharmacy Transaction Mapper
 */
@Mapper
public interface PharmacyTransactionMapper {

    PharmacyTransaction selectById(Long id);

    List<PharmacyTransaction> selectByDrugId(Long drugId);

    List<PharmacyTransaction> selectByType(String type);

    List<PharmacyTransaction> selectByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<PharmacyTransaction> selectByRegisterId(Long registerId);

    int insert(PharmacyTransaction transaction);
}
