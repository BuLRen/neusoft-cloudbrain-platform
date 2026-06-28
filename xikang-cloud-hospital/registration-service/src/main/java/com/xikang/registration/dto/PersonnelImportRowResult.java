package com.xikang.registration.dto;

import lombok.Data;

@Data
public class PersonnelImportRowResult {
    private int rowNumber;
    private String status;
    private String message;
    private Long employeeId;
    private String username;
}
