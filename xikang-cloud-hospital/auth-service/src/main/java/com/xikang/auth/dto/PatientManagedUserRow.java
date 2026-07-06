package com.xikang.auth.dto;

import lombok.Data;

@Data
public class PatientManagedUserRow {
    private Long userId;
    private String username;
    private String relation;
}
