package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.DrugInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Drug Info Mapper
 * <p>字段对齐线上 drug_info 真实表（drug_* 列）。</p>
 */
@Mapper
public interface DrugInfoMapper {

    DrugInfo selectById(Long id);

    /** 全量查询（兜底 LIMIT 200，防 OOM；正式列表请用 selectDrugsPage 分页） */
    List<DrugInfo> selectAll();

    List<DrugInfo> selectByKeyword(String keyword);

    List<DrugInfo> selectByDosageForm(String dosageForm);

    /**
     * 组合条件查询（AND），任一参数为空则忽略该条件。
     * dosageForm → drug_dosage；category → drug_type。
     */
    List<DrugInfo> selectByConditions(@Param("keyword") String keyword,
                                      @Param("dosageForm") String dosageForm,
                                      @Param("category") String category);

    /** 查询所有已用分类（drug_type）：西药/中成药/生物制品 */
    List<String> selectDrugTypes();

    /** 查询所有已用剂型（drug_dosage），供前端动态下拉 */
    List<String> selectDosageForms();

    List<DrugInfo> selectLowStock();

    // ==================== 分页查询（主列表） ====================

    /** 组合条件计数 */
    long countDrugs(@Param("keyword") String keyword,
                    @Param("dosageForm") String dosageForm,
                    @Param("category") String category);

    /** 组合条件分页查询（category → drug_type, dosageForm → drug_dosage） */
    List<DrugInfo> selectDrugsPage(@Param("keyword") String keyword,
                                   @Param("dosageForm") String dosageForm,
                                   @Param("category") String category,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    // ==================== 低库存分页 ====================

    long countLowStock();

    List<DrugInfo> selectLowStockPage(@Param("offset") int offset,
                                      @Param("limit") int limit);

    DrugInfo selectByCode(String code);

    int insert(DrugInfo drugInfo);

    int update(DrugInfo drugInfo);

    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int deleteById(Long id);
}
