package com.xikang.registration.mapper;

import com.xikang.registration.entity.Register;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Registration Mapper
 */
@Mapper
public interface RegistrationMapper {

    Register selectById(Long id);

    List<Register> selectByPatientId(Long patientId);

    List<Register> selectByStatus(Integer status);

    List<Register> selectByDate(LocalDate date);

    List<Register> selectByDepartmentAndDate(Long departmentId, LocalDate date);

    List<Register> selectByPayStatus(Integer payStatus);

    int insert(Register register);

    int update(Register register);

    int updateStatus(Long id, Integer status);

    int updatePayStatus(Long id, Integer payStatus);

    int deleteById(Long id);
}
