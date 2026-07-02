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

    /**
     * 按 ID 单条标记爽约（visit_state = 7）
     * 配合 Java 侧按时段判定逻辑：定时任务逐条算截止时间，过期的调本方法
     */
    int markMissedById(@Param("id") Long id);

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

    // ==================== 叫号系统（migration 027）====================

    /**
     * 选下一个待叫号：同医生、同天、已报到、未叫或过号，按报到时间升序取第一条。
     * 设计文档 §4.1 "叫下一个"的选取规则。
     * 注意：用 FOR UPDATE 锁住，防止两个医生同时叫到同一个号（虽然实际不会发生，但留作并发保护）。
     *
     * @param employeeId 医生 ID
     * @param visitDate  就诊日期（仅日期部分参与比较）
     */
    Register selectNextCallableForUpdate(@Param("employeeId") Long employeeId,
                                         @Param("visitDate") java.time.LocalDate visitDate);

    /**
     * 把指定挂号置为"已叫"状态：call_status=1, called_time=now, call_round+=1。
     * 用 call_status IN (0,3) 做乐观锁，避免并发覆盖。
     * 返回受影响行数，0 表示状态已被别人改了。
     */
    int markCalled(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    /**
     * 把指定挂号置为"已应答"：call_status=2, answered_time=now。
     * call_status=1 是预期前置态；用 IN (1) 做乐观锁。
     */
    int markAnswered(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    /**
     * 把指定挂号置为"过号"：call_status=3。
     * 前置态：call_status IN (0,1)。
     * 注意：过号不改 call_round（call_round 只在"叫号"时累加，达到 2 后才能确认过号终态）。
     */
    int markPassed(@Param("id") Long id);

    /**
     * 查询某医生今天"已叫未应答"的号（call_status=1）。
     * 用于医生工作站的"当前叫号"。
     */
    Register selectCurrentCallingByDoctor(@Param("employeeId") Long employeeId,
                                          @Param("visitDate") java.time.LocalDate visitDate);

    /**
     * 查询某科室今天所有"已叫"（call_status=1）的号，按 called_time desc。
     * 用于候诊大屏的"叫号板"。
     */
    List<Register> selectCallingByDepartment(@Param("departmentId") Long departmentId,
                                             @Param("visitDate") java.time.LocalDate visitDate);

    /**
     * 查询某科室今天所有候诊中（call_status IN 0,1,3）的号，按 check_in_time asc。
     * 用于大屏的"候诊队列"展示。
     */
    List<Register> selectWaitingByDepartment(@Param("departmentId") Long departmentId,
                                             @Param("visitDate") java.time.LocalDate visitDate);

    /**
     * 查询某医生今天所有候诊中的号（call_status IN 0,1,3）。
     * 用于医生工作站的患者列表加 call_status 标签。
     */
    List<Register> selectWaitingByDoctor(@Param("employeeId") Long employeeId,
                                         @Param("visitDate") java.time.LocalDate visitDate);

    /**
     * 查询所有"已叫未应答超过 5 分钟"的号（定时任务用）。
     */
    List<Register> selectTimeoutCandidates(@Param("deadline") java.time.LocalDateTime deadline);
}
