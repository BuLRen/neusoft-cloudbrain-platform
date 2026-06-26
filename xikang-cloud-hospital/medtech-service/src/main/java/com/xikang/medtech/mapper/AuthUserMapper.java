package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserMapper {

    AuthUser selectById(Long id);
}
