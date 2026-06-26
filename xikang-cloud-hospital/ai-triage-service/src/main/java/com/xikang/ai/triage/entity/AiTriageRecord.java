package com.xikang.ai.triage.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AiTriageRecord Entity - AI导诊记录表
 * ai_triage_record: id, patient_name, patient_age, patient_gender, symptom_description,
 *                   recommend_dept_id, recommend_dept_name, recommend_doctor_id, recommend_doctor_name,
 *                   risk_level, is_priority, ai_analysis, register_id, triage_time, model_id
 */
@Data
public class AiTriageRecord implements Serializable {





    private static final long serialVersionUID = 1L;

    private Long id;
    private String patientName;         // patient_name
    private Integer patientAge;         // patient_age
    private String patientGender;       // patient_gender
    private String symptomDescription;  // symptom_description (主症状)
    private Long recommendDeptId;      // recommend_dept_id
    private String recommendDeptName;   // recommend_dept_name
    private Long recommendDoctorId;    // recommend_doctor_id
    private String recommendDoctorName; // recommend_doctor_name
    private String riskLevel;          // risk_level: normal/urgent/critical
    private Integer isPriority;         // is_priority: 0/1
    private String aiAnalysis;          // ai_analysis (AI分析JSON)
    private Long registerId;            // register_id (关联挂号ID)
    private LocalDateTime triageTime;  // triage_time
    private String modelId;             // model_id
}