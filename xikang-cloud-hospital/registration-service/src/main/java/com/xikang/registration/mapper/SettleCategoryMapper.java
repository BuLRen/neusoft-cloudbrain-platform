package com.xikang.registration.mapper;

import com.xikang.registration.entity.SettleCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Settlement Category Mapper
 */
@Mapper
public interface SettleCategoryMapper {

    List<SettleCategory> selectAll();

    SettleCategory selectById(Long id);

    SettleCategory selectByCode(String code);

    int insert(SettleCategory category);

    int update(SettleCategory category);
}
