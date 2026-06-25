package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MedicationGuide Entity - 处方级用药指导单
 *
 * <p>按 register_id 聚合，一张处方一条记录。guide_content 存 AI 生成的结构化 JSON，
 * 下载 PDF 时实时渲染（PDF 不落盘）。</p>
 */
@Data
public class MedicationGuide implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long prescriptionId;
    private Long patientId;
    private String patientName;
    /** 结构化 JSON：{ items:[...], generalAdvice, interactionsNote, generatedAt, modelVersion } */
    private String guideContent;
    /** 产出方式：ai / fallback / manual */
    private String source;
    /** success / failed */
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
