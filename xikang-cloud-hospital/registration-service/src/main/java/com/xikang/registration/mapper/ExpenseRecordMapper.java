package com.xikang.registration.mapper;

import com.xikang.registration.entity.ExpenseRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Expense Record Mapper
 */
@Mapper
public interface ExpenseRecordMapper {

    ExpenseRecord selectById(Long id);

    List<ExpenseRecord> selectByRegisterId(Long registerId);

    List<ExpenseRecord> selectByPatientId(Long patientId);

    List<ExpenseRecord> selectByStatus(Integer status);

    List<ExpenseRecord> selectPendingByRegisterId(Long registerId);

    List<ExpenseRecord> selectPendingByRegisterAndIds(Long registerId, List<Long> itemIds);

    List<ExpenseRecord> selectUnrefundedByRegisterId(Long registerId);

    int insert(ExpenseRecord record);

    int update(ExpenseRecord record);

    int updateStatus(Long id, Integer status);

    int updatePayStatus(Long id, Integer status);
}
