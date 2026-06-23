package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方映射器（按挂号聚合读 + 更新 drug_state 写）。
 *
 * <p>真实 schema 中 prescription 表是"一药一行"的明细表，由 physician-service 写入。
 * pharmacy 侧的查询统一以 register_id 维度做 GROUP BY 聚合，
 * 得到一张"按挂号聚合的虚拟处方头"；发药/退药通过 UPDATE drug_state 完成。</p>
 *
 * <p>因此本接口中的 {@code prescriptionId} 参数在"读"场景下实际指挂号 id，
 * 在"写"场景下指具体行 id——见各方法注释。</p>
 */
@Mapper
public interface PrescriptionMapper {

    /**
     * 按行 id 反查所属挂号聚合的处方头。
     * 入参 id 为 prescription 行 id；返回值的 registerId 是真正的挂号 id。
     */
    Prescription selectById(Long id);

    /**
     * 按挂号 id 查询聚合处方头。
     */
    List<Prescription> selectByRegisterId(Long registerId);

    /**
     * 按挂号 id + 发药状态查聚合处方头。
     * status: 0 待发药 / 1 已发药 / 2 已退药
     */
    List<Prescription> selectByRegisterIdAndStatus(@Param("registerId") Long registerId,
                                                   @Param("status") Integer status);

    /**
     * 全局待发药列表（可按 registrationId 过滤）。
     * 待发药 = 该挂号下至少有一行 drug_state='未发'。
     */
    List<Prescription> selectPending(@Param("registrationId") Long registrationId);

    /**
     * 历史处方组合查询（按 patientId / 状态 / 日期范围）。
     * 所有参数为 null 时返回全部按挂号聚合的处方头。
     */
    List<Prescription> selectByConditions(@Param("patientId") Long patientId,
                                          @Param("status") Integer status,
                                          @Param("startDate") java.time.LocalDateTime startDate,
                                          @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * 按 patient_id 反查历史处方（聚合头）。
     */
    List<Prescription> selectByPatientId(Long patientId);

    /**
     * 更新单行处方的发药状态。
     * 入参 id 为 prescription 行 id；stateText 取值：'已发' / '已退'。
     */
    int updateDispensationStatus(@Param("id") Long id,
                                 @Param("stateText") String stateText);

    /**
     * 更新单个挂号下所有 drug_state=oldStateText 的处方行，改为 newStateText，
     * 并写入发药时间与药师姓名。
     * 入参 registerId 是挂号 id。
     */
    int updateStateByRegister(@Param("registerId") Long registerId,
                              @Param("oldStateText") String oldStateText,
                              @Param("newStateText") String newStateText,
                              @Param("dispensationTime") LocalDateTime dispensationTime,
                              @Param("pharmacist") String pharmacist);

    /**
     * 兼容旧调用的精确更新方法。内部等同于 {@link #updateStateByRegister}，
     * 将该挂号下全部状态更新为 status 对应的中文文本。
     */
    int updateDispensationInfo(@Param("registerId") Long registerId,
                               @Param("status") Integer status,
                               @Param("dispensationTime") LocalDateTime dispensationTime,
                               @Param("pharmacist") String pharmacist);
}
