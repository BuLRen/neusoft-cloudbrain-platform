package com.xikang.auth.mapper;

import com.xikang.auth.dto.PatientAdminView;
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

    /**
     * 行级锁查询患者（用于钱包流水事务内取余额）
     */
    Patient selectByIdForUpdate(@Param("id") Integer id);

    /**
     * 覆盖式更新患者余额（事务内与流水写入搭配使用）
     */
    int updateBalance(@Param("id") Integer id, @Param("accountBalance") BigDecimal accountBalance);

    /**
     * 删除患者
     */
    int deleteById(@Param("id") Integer id);

    /**
     * 按姓名/手机号/档案号模糊搜索（现场收费窗口用）
     */
    List<Patient> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    long countForAdmin(@Param("keyword") String keyword, @Param("includeDisabled") Boolean includeDisabled);

    List<PatientAdminView> selectPageForAdmin(
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    PatientAdminView selectAdminViewById(@Param("id") Integer id);

    int restoreById(@Param("id") Integer id);
}
