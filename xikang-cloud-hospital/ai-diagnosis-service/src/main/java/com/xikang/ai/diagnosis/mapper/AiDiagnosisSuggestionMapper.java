package com.xikang.ai.diagnosis.mapper;

import com.xikang.ai.diagnosis.entity.AiDiagnosisSuggestion;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Diagnosis Suggestion Mapper
 */
@Mapper
public interface AiDiagnosisSuggestionMapper {

    AiDiagnosisSuggestion selectById(Long id);

    AiDiagnosisSuggestion selectByRequestId(Long requestId);

    List<AiDiagnosisSuggestion> selectByPatientId(Long patientId);

    List<AiDiagnosisSuggestion> selectByStatus(Integer status);

    int insert(AiDiagnosisSuggestion suggestion);

    int update(AiDiagnosisSuggestion suggestion);
}
