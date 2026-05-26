package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Triage Desk Record Entity - AI分诊台记录
 * 汇总导诊结果，供管理员或挂号人员人工确认
 */
@Data
public class TriageDeskRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;               // 患者ID
    private String patientName;           // 患者姓名
    private String patientPhone;         // 联系方式
    private String symptoms;            // 患者描述的症状
    private String aiTriageResult;       // AI导诊结果JSON
    private Long recommendedDepartmentId; // 推荐科室ID
    private String recommendedDepartment; // 推荐科室名称
    private Long recommendedPhysicianId;  // 推荐医生ID
    private String recommendedPhysicianName; // 推荐医生姓名
    private String riskLevel;           // 风险等级：low/medium/high
    private String aiAnalysis;           // AI分析详情JSON
    private Integer status;              // 状态：0待确认/1已确认/2已挂号/3已取消
    private Long confirmedDepartmentId;  // 确认后的科室ID
    private String confirmedDepartment;   // 确认后的科室名称
    private Long confirmedPhysicianId;    // 确认后的医生ID
    private String confirmedPhysicianName; // 确认后的医生姓名
    private Long operatorId;             // 操作员ID
    private String operatorName;          // 操作员姓名
    private String confirmRemark;         // 确认备注
    private LocalDateTime createTime;
    private LocalDateTime confirmTime;    // 确认时间
}
