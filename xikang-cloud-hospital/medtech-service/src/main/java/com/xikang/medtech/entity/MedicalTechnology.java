package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Medical Technology Entity - 医技项目表（检查/检验/处置）
 */
@Data
public class MedicalTechnology implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String techCode;
    private String techName;
    private String techFormat;
    private BigDecimal techPrice;
    /** check / inspection / disposal */
    private String techType;
    private String priceType;
    private Long deptmentId;
    private String aiCategoryCode;
    /** 查询联表展示，非表字段 */
    private String deptName;
}
