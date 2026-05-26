package com.xikang.ai.consult.mapper;

import com.xikang.ai.consult.entity.AiPreVisitRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Pre-visit Record Mapper
 */
@Mapper
public interface AiPreVisitRecordMapper {

    AiPreVisitRecord selectById(Long id);

    AiPreVisitRecord selectByRegisterId(Long registerId);

    AiPreVisitRecord selectByPatientId(Long patientId);

    List<AiPreVisitRecord> selectByStatus(Integer status);

    int insert(AiPreVisitRecord record);

    int update(AiPreVisitRecord record);
}
