package com.xikang.ai.consult.mapper;

import com.xikang.ai.consult.entity.AiPreVisitRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI Pre-visit Record Mapper
 */
@Mapper
public interface AiPreVisitRecordMapper {

    AiPreVisitRecord selectById(Integer id);

    /** 按会话UUID查询所有轮次，按 round_number 升序 */
    List<AiPreVisitRecord> selectBySessionUuid(String sessionUuid);

    /** 按挂号ID查询所有轮次 */
    List<AiPreVisitRecord> selectByRegisterId(Integer registerId);

    /** 按患者ID + 状态查询 */
    List<AiPreVisitRecord> selectByPatientIdAndState(Integer patientId, String state);

    /** 同一会话下一轮 round_number */
    Integer selectMaxRoundNumber(String sessionUuid);

    int insert(AiPreVisitRecord record);

    int update(AiPreVisitRecord record);

    /** 更新整个会话的状态/汇总字段（不依赖单条 id） */
    int updateSummaryBySessionUuid(AiPreVisitRecord record);

    /** 查询患者档案里登记的过敏史（patient.allergy_history），用于预问诊时确认 */
    String selectPatientAllergy(Integer patientId);
}
