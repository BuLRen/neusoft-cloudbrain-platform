package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.PrescriptionDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 处方明细映射器。
 *
 * <p>底层实际查询的是 prescription 表（一药一行），按 register_id 聚合取明细。
 * 因此 {@code prescriptionId} 参数的真实语义是 register_id——保留旧参数名仅为
 * 避免大面积重命名，使用时需清楚它实际按挂号过滤。</p>
 */
@Mapper
public interface PrescriptionDetailMapper {

    /**
     * 按挂号 id 查询处方明细行。
     * 参数名沿用 prescriptionId，实际匹配 prescription.register_id。
     */
    List<PrescriptionDetail> selectByPrescriptionId(Long prescriptionId);
}
