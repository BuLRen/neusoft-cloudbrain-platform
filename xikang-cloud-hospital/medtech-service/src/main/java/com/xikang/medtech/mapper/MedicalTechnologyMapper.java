package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.MedicalTechnology;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Medical Technology Mapper
 */
@Mapper
public interface MedicalTechnologyMapper {

    MedicalTechnology selectById(Long id);

    List<MedicalTechnology> selectAll();

    List<MedicalTechnology> selectByType(String type);

    List<MedicalTechnology> selectByDepartment(Long departmentId);

    MedicalTechnology selectByCode(String code);

    int insert(MedicalTechnology medicalTechnology);

    int update(MedicalTechnology medicalTechnology);

    int deleteById(Long id);
}
