package com.xikang.registration.mapper;

import com.xikang.registration.entity.TriageDeskRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Triage Desk Record Mapper
 */
@Mapper
public interface TriageDeskRecordMapper {

    TriageDeskRecord selectById(Long id);

    List<TriageDeskRecord> selectByStatus(Integer status);

    List<TriageDeskRecord> selectPending();

    List<TriageDeskRecord> selectByPatientId(Long patientId);

    int insert(TriageDeskRecord record);

    int update(TriageDeskRecord record);

    int updateStatus(Long id, Integer status);

    int updateConfirmation(Long id, Long departmentId, String departmentName,
                          Long physicianId, String physicianName, Long operatorId,
                          String operatorName, String remark);
}
