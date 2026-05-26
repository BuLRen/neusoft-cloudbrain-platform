package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prescription Entity - 处方表（由physician-service管理，人员B只读和更新发药状态）
 */
@Data
public class Prescription implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;           // 挂号ID
    private Long patientId;           // 患者ID
    private String patientName;       // 患者姓名
    private Long physicianId;         // 开单医生ID
    private String physicianName;     // 开单医生姓名
    private String diagnosis;         // 诊断
    private BigDecimal totalAmount;   // 总金额
    private Integer dispensationStatus; // 发药状态：0待发药/1已发药/2已退药
    private LocalDateTime dispensationTime; // 发药时间
    private String pharmacist;        // 药师姓名
    private String remarks;           // 备注
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
