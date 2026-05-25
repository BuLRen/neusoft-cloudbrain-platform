package com.xikang.registration.mapper;

import com.xikang.registration.entity.Registration;
import org.apache.ibatis.annotations.Mapper;

/**
 * Registration Mapper
 */
@Mapper
public interface RegistrationMapper {

    Registration selectById(Long id);

    int insert(Registration registration);

    int update(Registration registration);

    int deleteById(Long id);
}
