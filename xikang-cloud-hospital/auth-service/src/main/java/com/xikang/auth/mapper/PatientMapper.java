package com.xikang.auth.mapper;

import com.xikang.auth.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Patient Mapper - 患者档案 Mapper
 */
@Mapper
public interface PatientMapper {

    /**
     * 根据ID查询患者
     */
    Patient selectById(@Param("id") Integer id);

    /**
     * 根据身份证号查询患者
     */
    Patient selectByIdCard(@Param("idCard") String idCard);

    /**
     * 根据用户ID查询管理的患者列表
     */
    List<Patient> selectByUserId(@Param("userId") Long userId);

    /**
     * 新增患者
     */
    int insert(Patient patient);

    /**
     * 更新患者
     */
    int update(Patient patient);

    BigDecimal selectBalanceById(@Param("id") Integer id);

    int rechargeBalance(@Param("id") Integer id, @Param("amount") BigDecimal amount);

    int deductBalanceIfEnough(@Param("id") Integer id, @Param("amount") BigDecimal amount);

    /**
     * 删除患者
     */
    int deleteById(@Param("id") Integer id);
}