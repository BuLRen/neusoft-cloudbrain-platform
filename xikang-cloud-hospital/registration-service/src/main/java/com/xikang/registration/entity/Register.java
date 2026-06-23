package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Register Entity - 挂号记录表
 * register: id, patient_id, scheduling_id, case_number, real_name, gender, card_number,
 *           birthdate, age, age_type, home_address, visit_date, noon, deptment_id,
 *           employee_id, regist_level_id, settle_category_id, is_book, regist_method,
 *           regist_money, visit_state
 */
@Data
public class Register implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;       // patient_id (患者ID)
    private Long schedulingId;    // scheduling_id (排班ID)
    private String caseNumber;     // case_number (病历号)
    private String realName;      // real_name (患者姓名)
    private String gender;        // gender
    private String cardNumber;    // card_number (就诊卡号)
    private LocalDate birthdate;  // birthdate
    private Integer age;           // age
    private String ageType;       // age_type
    private String homeAddress;   // home_address
    private LocalDateTime visitDate;  // visit_date
    private String noon;           // noon (上午/下午)
    private Long deptmentId;      // deptment_id (科室ID)
    private Long employeeId;      // employee_id (医生ID)
    private Long registLevelId;   // regist_level_id
    private Long settleCategoryId; // settle_category_id
    private String isBook;        // is_book (是/否)
    private String registMethod;  // regist_method
    private BigDecimal registMoney; // regist_money (挂号费)
    private Integer visitState;   // visit_state (1已挂号/2医生接诊/3看诊结束/4已退号/5爽约)
    private java.time.LocalDateTime checkInTime; // check_in_time (报到时间，NULL=未报到)

    // 辅助字段（不映射数据库）
    private String departmentName;
    private String physicianName;
    private String registLevelName;
    private String settleCategoryName;
    private String aiTriageResult;
    private String aiPreVisit;
    private String complaint;
    private String patientPhone;
    private String idCard;
}