package com.xikang.registration.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DrugCatalogView {
    private Long id;
    private String name;
    private String genericName;
    private String brandName;
    private String specification;
    private String dosageForm;
    private String category;
    private String unit;
    private String manufacturer;
    private String approvalNumber;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer lowStockThreshold;
    private String storageConditions;
    private String instructions;
    private String contraindications;
    private String adverseReactions;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
