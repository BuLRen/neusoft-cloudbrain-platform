package com.xikang.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * UserPatientManaged Mapper - 用户管理患者关联 Mapper
 */
@Mapper
public interface UserPatientManagedMapper {

    /**
     * 查询用户管理的患者ID列表
     */
    java.util.List<Integer> selectPatientIdsByUserId(@Param("userId") Long userId);
}