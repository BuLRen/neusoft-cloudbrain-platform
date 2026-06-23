package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.Dispensing;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Dispensing Mapper - P2-4.6 发药单
 */
@Mapper
public interface DispensingMapper {

    /** 按挂号 ID 查询所有发药单（关联 prescription.register_id） */
    List<Dispensing> selectByRegisterId(Long registerId);

    /** 按处方 ID 查询发药单 */
    List<Dispensing> selectByPrescriptionId(Long prescriptionId);

    /** 按患者 ID 查询发药单 */
    List<Dispensing> selectByPatientId(Long patientId);

    int insert(Dispensing dispensing);
}
