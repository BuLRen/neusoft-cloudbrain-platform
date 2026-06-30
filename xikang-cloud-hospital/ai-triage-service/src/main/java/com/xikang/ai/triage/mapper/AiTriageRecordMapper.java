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

    AiTriageRecord selectByRegisterId(Integer registerId);

    List<AiTriageRecord> selectByPatientId(Long patientId);

    List<AiTriageRecord> selectPending();

    int insert(AiTriageRecord record);

    int update(AiTriageRecord record);

    /**
     * 按 sessionId 精确回填 register_id。
     * 替代旧的 updateRegisterIdByPatient（按"猜最近一条"回填，曾导致导诊记录错绑到错误的挂号）。
     * sessionId 是导诊创建时生成的 UUID，由前端透传到挂号，保证 1:1 精确绑定。
     *
     * @param sessionId 导诊会话 ID
     * @param registerId 挂号 ID
     * @return 受影响行数（0 表示没找到匹配记录，1 表示回填成功）
     */
    int updateRegisterIdBySessionId(@org.apache.ibatis.annotations.Param("sessionId") String sessionId,
                                    @org.apache.ibatis.annotations.Param("registerId") Long registerId);

    /**
     * 按 patientId 查最近一条导诊记录（保留给历史/统计查询用，预问诊不再依赖）。
     */
    AiTriageRecord selectLatestByPatientId(@org.apache.ibatis.annotations.Param("patientId") Long patientId);
}
