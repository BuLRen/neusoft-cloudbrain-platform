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

    /**
     * 插入用户-患者关联关系
     */
    int insert(@Param("userId") Long userId, @Param("patientId") Integer patientId, @Param("relation") String relation);

    /**
     * 删除用户-患者关联关系
     */
    int deleteByIds(@Param("userId") Long userId, @Param("patientId") Integer patientId);
}