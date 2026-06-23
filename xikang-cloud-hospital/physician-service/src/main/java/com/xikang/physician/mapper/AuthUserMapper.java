package com.xikang.physician.mapper;

import com.xikang.physician.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthUserMapper {

    AuthUser selectById(@Param("id") Long id);
}
