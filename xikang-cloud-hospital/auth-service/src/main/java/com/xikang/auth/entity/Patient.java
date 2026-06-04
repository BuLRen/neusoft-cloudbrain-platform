package com.xikang.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Patient Entity - 患者档案表
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
    private String relation;
    private Integer isPrimary;
    private Integer delmark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}