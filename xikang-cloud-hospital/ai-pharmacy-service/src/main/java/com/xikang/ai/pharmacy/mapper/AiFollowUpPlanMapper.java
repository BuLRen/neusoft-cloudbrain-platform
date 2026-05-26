package com.xikang.ai.pharmacy.mapper;

import com.xikang.ai.pharmacy.entity.AiFollowUpPlan;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Follow-up Plan Mapper
 */
@Mapper
public interface AiFollowUpPlanMapper {

    AiFollowUpPlan selectById(Long id);

    List<AiFollowUpPlan> selectByPatientId(Long patientId);

    List<AiFollowUpPlan> selectByRegisterId(Long registerId);

    List<AiFollowUpPlan> selectByPrescriptionId(Long prescriptionId);

    List<AiFollowUpPlan> selectByStatus(Integer status);

    int insert(AiFollowUpPlan plan);

    int update(AiFollowUpPlan plan);
}
