package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Drug Info Entity - 药品信息表
 * <p>字段对齐线上 drug_info 真实表（官方目录 drug_* 列 + 业务扩展列）。
 * 真实数据在 drug_* 列；旧 name/generic_name 等列保留在表中但本实体不再使用。</p>
 */
@Data
public class DrugInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String drugCode;            // drug_code 药品编码（唯一）
    private String drugName;            // drug_name 药品名称
    private String drugFormat;          // drug_format 规格
    private String drugUnit;            // drug_unit 单位
    private String manufacturer;        // manufacturer 生产企业
    private String drugDosage;          // drug_dosage 剂型（片剂/注射剂/胶囊剂/...）
    private String drugType;            // drug_type 分类（西药/中成药/生物制品）
    private BigDecimal drugPrice;       // drug_price 单价
    private String mnemonicCode;        // mnemonic_code 拼音助记码
    private LocalDate creationDate;     // creation_date 录入日期

    // ===== 业务扩展列（drug_info 表内，旧 pharmacy 模块维护） =====
    private Integer stockQuantity;      // stock_quantity 汇总库存
    private Integer lowStockThreshold;  // low_stock_threshold 低库存阈值
    private String storageConditions;   // storage_conditions 储存条件
    private String instructions;        // instructions 用药指导
    private String contraindications;   // contraindications 禁忌症
    private String adverseReactions;    // adverse_reactions 不良反应
    private Integer status;             // status 0禁用/1启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
