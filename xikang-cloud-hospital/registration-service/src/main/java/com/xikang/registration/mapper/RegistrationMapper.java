package com.xikang.registration.mapper;

import com.xikang.registration.entity.Register;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Registration Mapper
 */
@Mapper
public interface RegistrationMapper {

    Register selectById(Long id);

    List<Register> selectByPatientId(Long patientId);

    /**
     * 查询当前登录用户管理的所有就诊人（本人+家属）的挂号记录。
     * 通过 user_patient_managed 关联，结果带 relation 字段（本人/配偶/父母等）。
     * 用于"我的挂号"列表：本人 + 家属挂号都能看到。
     */
    List<Register> selectByManagedUserId(Long userId);

    /**
     * 查询爽约候选：visit_state = 1 且就诊时间早于指定时间
     */
    List<Register> selectMissedCandidates(@Param("deadline") java.time.LocalDateTime deadline);

    /**
     * 批量将过期未到诊的挂号置为爽约（visit_state = 5）
     * @return 受影响行数
     */
    int markMissed(@Param("deadline") java.time.LocalDateTime deadline);

    List<Register> selectByStatus(Integer status);

    List<Register> selectByDate(LocalDate date);

    List<Register> selectByDepartmentAndDate(Long departmentId, LocalDate date);

    List<Register> selectByPayStatus(Integer payStatus);

    int insert(Register register);

    Long selectMaxId();

    Long selectNextSequenceValue();

    Long syncIdSequence();

    int update(Register register);

    int updateStatus(@Param("id") Long id, @Param("visitState") Integer status);

    String selectLatestCaseNumberByPatient(@Param("patientId") Long patientId);

    int updatePayStatus(@Param("id") Long id, @Param("visitState") Integer payStatus);

    /**
     * 设置报到时间（患者扫码报到）
     */
    int updateCheckInTime(@Param("id") Long id, @Param("checkInTime") java.time.LocalDateTime checkInTime);

    /**
     * 统计同一医生、同一天、已报到但未接诊的患者数（用于计算号序和前面等待人数）
     */
    int countWaitingBefore(@Param("id") Long id);

    int deleteById(Long id);
}
