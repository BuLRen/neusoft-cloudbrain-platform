package com.xikang.schedule.client.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeDTO implements Serializable {
    private Long id;
    private Long deptmentId;
    private Long registLevelId;
    private String realname;
    private Integer delmark;
    private String deptName;
    private String registName;
}
