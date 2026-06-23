package com.xikang.auth.mapper;

import com.xikang.auth.entity.PatientBalanceTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Patient Balance Transaction Mapper - 患者余额流水 Mapper
 */
@Mapper
public interface PatientBalanceTransactionMapper {

    int insert(PatientBalanceTransaction transaction);

    PatientBalanceTransaction selectByBusiness(@Param("patientId") Integer patientId,
                                               @Param("transactionType") String transactionType,
                                               @Param("businessType") String businessType,
                                               @Param("businessId") Long businessId);

    List<PatientBalanceTransaction> selectByPatient(@Param("patientId") Integer patientId,
                                                    @Param("transactionType") String transactionType);
}
