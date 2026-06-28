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
     * 挂号成功后回填 register_id：定位该患者最近一条 register_id 为空的导诊记录并更新。
     * 用于"导诊 → 预问诊"上下文串联（导诊时还没有 registerId，需要挂号后回填）。
     *
     * @param patientId  患者 ID
     * @param registerId 挂号 ID
     * @return 受影响行数（0 表示没找到匹配记录，1 表示回填成功）
     */
    int updateRegisterIdByPatient(@org.apache.ibatis.annotations.Param("patientId") Long patientId,
                                  @org.apache.ibatis.annotations.Param("registerId") Long registerId);

    /**
     * 按 patientId 查最近一条导诊记录（getTriageSummary 兜底用：registerId 查不到时退而求其次）。
     */
    AiTriageRecord selectLatestByPatientId(@org.apache.ibatis.annotations.Param("patientId") Long patientId);
}
