package com.xikang.payment.mapper;

import com.xikang.payment.entity.ExpenseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Expense Record Mapper（v3.2：唯一拥有者，所有其他服务通过 Feign 调用 payment-service 间接读写）
 */
@Mapper
public interface ExpenseRecordMapper {

    ExpenseRecord selectById(Long id);

    List<ExpenseRecord> selectByRegisterId(Long registerId);

    List<ExpenseRecord> selectByPatientId(Long patientId);

    List<ExpenseRecord> selectByStatus(Integer status);

    List<ExpenseRecord> selectPendingByRegisterId(Long registerId);

    List<ExpenseRecord> selectPendingByPatientId(Long patientId);

    List<ExpenseRecord> selectPendingByRegisterAndIds(@Param("registerId") Long registerId, @Param("itemIds") List<Long> itemIds);

    /**
     * 查该挂号下的药品费行（item_code='MEDICATION_FEE'）。
     * 用于 pharmacy.assertMedicationPaid / 患者支付药品费时定位待缴行。
     */
    ExpenseRecord selectMedicationFeeByRegisterId(Long registerId);

    /**
     * 查该挂号下的挂号费行（item_code='REGISTRATION_FEE'）。
     */
    ExpenseRecord selectRegistrationFeeByRegisterId(Long registerId);

    ExpenseRecord selectByRegisterSourceAndItemCode(@Param("registerId") Long registerId,
                                                    @Param("sourceId") Long sourceId,
                                                    @Param("itemCode") String itemCode);

    List<ExpenseRecord> selectPendingByRegisterIdAll(Long registerId);

    /**
     * 多条件查询（v3.2 §4.2 internal/records）：patientId/registerId/status/timeRange 任选。
     */
    List<ExpenseRecord> queryRecords(@Param("patientId") Long patientId,
                                     @Param("registerId") Long registerId,
                                     @Param("status") Integer status,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 管理员订单列表：按 create_time 过滤与关键词搜索（患者姓名 / 挂号号）。
     */
    List<ExpenseRecord> selectForAdminOrderList(@Param("keyword") String keyword,
                                                @Param("patientId") Long patientId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 幂等 INSERT：
     * - MEDICATION_FEE：依赖既有 partial unique index uq_expense_record_medication_fee
     *                   ON CONFLICT DO NOTHING（v3.2 §3.1.1）
     * - 其他 item_code：普通 INSERT（REGISTRATION_FEE 由 service 层先 SELECT 后 INSERT 防重）
     * 返回 useGeneratedKeys 回填的 id。
     */
    int insert(ExpenseRecord record);

    /**
     * 仅 MEDICATION_FEE 用：ON CONFLICT DO NOTHING。
     */
    int insertMedicationFeeIfAbsent(ExpenseRecord record);

    /**
     * 每日已缴费金额聚合（v3.2 替代 StatsMapper.dailyTrend 中的 charge 子查询）。
     */
    List<java.util.Map<String, Object>> dailyCharges(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /**
     * 清理 orphan 行（v3.2 §4.2 定时任务）：status=0 且 register 不存在且创建超过 10 分钟。
     */
    int invalidateOrphans(@Param("cutoff") LocalDateTime cutoff);

    int update(ExpenseRecord record);

    /**
     * 行级锁定的状态查询，配合二次校验防双扣（v3.2 §4.2 payItem 实现）。
     */
    ExpenseRecord selectByIdForUpdate(Long id);
}
