package com.xikang.auth.mapper;

import com.xikang.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * User Mapper
 */
@Mapper
public interface UserMapper {

    User selectByUsername(String username);

    User selectById(Long id);

    User selectByEmployeeId(@Param("employeeId") Integer employeeId);

    int insert(User user);

    int update(User user);

    int deleteById(Long id);

    int updatePassword(@Param("id") Long id, @Param("newPassword") String newPassword);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateRealNameByEmployeeId(@Param("employeeId") Integer employeeId, @Param("realName") String realName);
}
