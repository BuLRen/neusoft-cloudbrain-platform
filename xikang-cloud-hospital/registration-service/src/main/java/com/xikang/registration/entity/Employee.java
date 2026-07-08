package com.xikang.registration.entity;

import lombok.Data;

/**
 * Employee Entity - 员工实体
 * 用于查询科室医生
 */
@Data
public class Employee {
    private Long id;
    private Long deptmentId;
    private Long registLevelId;
    private String realname;
    private Integer delmark;
    /** 诊室名称，候诊大屏展示用 */
    private String clinicRoom;

    // 扩展字段（用于联表查询）
    private String deptName;
    private String registName;
}
