package com.xikang.pharmacy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用记录 Mapper（药房侧只读 + 出账写入）。
 *
 * <p>expense_record 表的逻辑属主是 registration-service，但本系统采用共享单库模式，
 * pharmacy-service 通过本 mapper 直接读写 expense_record 行：</p>
 * <ul>
 *   <li>出账：医生开药完成后，药房在患者首次查询时幂等写一行 MEDICATION_FEE（status=0）。</li>
 *   <li>校验：发药前判断该挂号的 MEDICATION_FEE 行 status 是否为 1（已缴费）。</li>
 * </ul>
 */
@Mapper
public interface ExpenseRecordMapper {

    /**
     * 查该挂号下的药品费行（不管 status）。
     * 用于幂等判断"是否已出账"和"出账后是否已缴费"。
     */
    Integer selectMedicationFeeStatus(@Param("registerId") Long registerId);

    /**
     * 幂等插入一行药品费。item_code 固定为 MEDICATION_FEE。
     * 若该挂号已存在 MEDICATION_FEE 行（部分唯一索引兜底），DO NOTHING。
     *
     * @param initialStatus 初始状态：金额&gt;0 时传 0（待缴费），金额=0 时传 1（直接视为已结清）。
     */
    int insertMedicationFee(@Param("registerId") Long registerId,
                            @Param("patientId") Long patientId,
                            @Param("patientName") String patientName,
                            @Param("totalAmount") BigDecimal totalAmount,
                            @Param("initialStatus") int initialStatus,
                            @Param("remark") String remark,
                            @Param("createTime") LocalDateTime createTime);
}
