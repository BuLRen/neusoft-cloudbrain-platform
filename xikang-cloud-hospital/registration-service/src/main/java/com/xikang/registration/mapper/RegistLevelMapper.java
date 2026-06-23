package com.xikang.registration.mapper;

import com.xikang.registration.entity.RegistLevel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Registration Level Mapper
 */
@Mapper
public interface RegistLevelMapper {

    List<RegistLevel> selectAll();

    RegistLevel selectById(Long id);

    RegistLevel selectByCode(String code);

    int insert(RegistLevel registLevel);

    int update(RegistLevel registLevel);

    int deleteById(Long id);
}
