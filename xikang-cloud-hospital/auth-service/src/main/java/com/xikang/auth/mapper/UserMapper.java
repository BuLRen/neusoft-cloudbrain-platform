package com.xikang.auth.mapper;

import com.xikang.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper
 */
@Mapper
public interface UserMapper {

    User selectByUsername(String username);

    User selectById(Long id);

    int insert(User user);

    int update(User user);

    int deleteById(Long id);

    int updatePassword(Long id, String newPassword);
}
