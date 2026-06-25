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

    List<ExpenseRecord> selectPendingByPatientId(Long patientId);

    List<ExpenseRecord> selectPendingByRegisterAndIds(Long registerId, List<Long> itemIds);

    List<ExpenseRecord> selectUnrefundedByRegisterId(Long registerId);

    /**
     * 查该挂号下的药品费行（item_code='MEDICATION_FEE'）。
     * 用于患者端支付药品费时定位待缴行。
     */
    ExpenseRecord selectMedicationFeeByRegisterId(Long registerId);

    int insert(ExpenseRecord record);

    int update(ExpenseRecord record);

    int updateStatus(Long id, Integer status);

    int updatePayStatus(Long id, Integer status);
}
