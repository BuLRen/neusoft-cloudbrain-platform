package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Drug Stock Entity - 药品库存表
 */
@Data
public class DrugStock implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long drugId;             // 药品ID
    private String batchNumber;      // 批号
    private Integer quantity;        // 数量
    private LocalDate productionDate; // 生产日期
    private LocalDate expiryDate;    // 有效期至
    private Integer status;           // 状态：0冻结/1可用
    private String location;         // 存放位置
    private LocalDateTime createTime;
}
