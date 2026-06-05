package com.xikang.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Patient Entity - 患者档案表（本人+家人）
 */
@Data
public class Patient implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String realName;
    private String idCard;
    private String gender;
    private LocalDate birthdate;
    private String phone;
    private String avatar;
    private String homeAddress;
    private String allergyHistory;
    private Integer delmark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // 关系（从 user_patient_managed 表联表查询获取）
    private String relation;
}