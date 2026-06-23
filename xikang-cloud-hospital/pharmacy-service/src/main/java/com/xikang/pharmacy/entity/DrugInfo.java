package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Drug Info Entity - 药品信息表
 */
@Data
public class DrugInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;                 // 药品名称
    private String genericName;          // 通用名
    private String brandName;           // 品牌名
    private String specification;       // 规格
    private String dosageForm;          // 剂型：片剂/胶囊/注射液/颗粒等
    private String category;            // P1-4.3 分类：抗生素/OTC/中成药/处方药等
    private String unit;                 // 单位
    private String manufacturer;        // 生产企业
    private String approvalNumber;       // 批准文号
    private BigDecimal price;           // 单价
    private Integer stockQuantity;      // 库存数量
    private Integer lowStockThreshold;  // 低库存阈值
    private String storageConditions;   // 储存条件
    private String instructions;        // 用药指导
    private String contraindications;   // 禁忌症
    private String adverseReactions;    // 不良反应
    private Integer status;             // 状态：0禁用/1启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
