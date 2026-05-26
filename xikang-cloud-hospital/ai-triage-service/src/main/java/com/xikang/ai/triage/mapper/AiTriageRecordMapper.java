package com.xikang.ai.triage.mapper;

import com.xikang.ai.triage.entity.AiTriageRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Triage Record Mapper
 */
@Mapper
public interface AiTriageRecordMapper {

    AiTriageRecord selectById(Long id);

    AiTriageRecord selectBySessionId(String sessionId);

    List<AiTriageRecord> selectByPatientId(Long patientId);

    List<AiTriageRecord> selectPending();

    int insert(AiTriageRecord record);

    int update(AiTriageRecord record);
}
