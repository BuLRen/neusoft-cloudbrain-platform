package com.xikang.ctviewer.mapper;

import com.xikang.ctviewer.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthUserMapper {

    AuthUser selectById(@Param("id") Long id);
}
