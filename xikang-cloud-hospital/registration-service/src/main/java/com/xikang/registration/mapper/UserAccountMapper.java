package com.xikang.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface UserAccountMapper {

    Map<String, Object> selectByEmployeeId(@Param("employeeId") Long employeeId);

    int insertPhysicianAccount(Map<String, Object> row);

    int insertMedtechAccount(Map<String, Object> row);

    int insertFollowUpAccount(Map<String, Object> row);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    int updateRealNameByEmployeeId(@Param("employeeId") Long employeeId, @Param("realName") String realName);

    int countByUsername(@Param("username") String username);
}
