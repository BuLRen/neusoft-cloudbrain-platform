package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface CriticalValueContextMapper {

    Map<String, Object> selectRegisterDoctor(@Param("registerId") Long registerId);
}
