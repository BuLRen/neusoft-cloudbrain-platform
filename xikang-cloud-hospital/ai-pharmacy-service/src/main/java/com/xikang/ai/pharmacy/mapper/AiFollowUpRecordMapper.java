package com.xikang.ai.pharmacy.mapper;

import com.xikang.ai.pharmacy.entity.AiFollowUpRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Follow-up Record Mapper
 */
@Mapper
public interface AiFollowUpRecordMapper {

    AiFollowUpRecord selectById(Long id);

    List<AiFollowUpRecord> selectByPlanId(Long planId);

    List<AiFollowUpRecord> selectByPatientId(Long patientId);

    int insert(AiFollowUpRecord record);
}
