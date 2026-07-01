package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowUpLastVisitMapper {

    Map<String, Object> selectByRegisterId(@Param("registerId") Long registerId);
}
