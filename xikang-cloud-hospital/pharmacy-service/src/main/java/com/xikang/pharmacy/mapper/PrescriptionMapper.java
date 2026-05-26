package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Prescription Mapper
 */
@Mapper
public interface PrescriptionMapper {

    Prescription selectById(Long id);

    List<Prescription> selectByRegisterId(Long registerId);

    List<Prescription> selectByRegisterIdAndStatus(@Param("registerId") Long registerId, @Param("status") Integer status);

    List<Prescription> selectByPatientId(Long patientId);

    List<Prescription> selectPending();

    int insert(Prescription prescription);

    int update(Prescription prescription);

    int updateDispensationStatus(@Param("id") Long id, @Param("status") Integer status);
}
