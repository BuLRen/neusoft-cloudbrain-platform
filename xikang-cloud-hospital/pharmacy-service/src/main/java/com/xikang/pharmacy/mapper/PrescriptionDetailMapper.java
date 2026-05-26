package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.PrescriptionDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Prescription Detail Mapper
 */
@Mapper
public interface PrescriptionDetailMapper {

    PrescriptionDetail selectById(Long id);

    List<PrescriptionDetail> selectByPrescriptionId(Long prescriptionId);

    int insert(PrescriptionDetail detail);

    int deleteById(Long id);
}
