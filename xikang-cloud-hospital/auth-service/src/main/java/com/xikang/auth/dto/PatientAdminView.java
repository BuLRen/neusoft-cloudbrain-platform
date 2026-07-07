package com.xikang.auth.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PatientAdminView {

    private Integer id;
    private String realName;
    private String idCard;
    private String gender;
    private LocalDate birthdate;
    private String phone;
    private String homeAddress;
    private String allergyHistory;
    private BigDecimal accountBalance;
    private Integer delmark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ManagedUser> managedUsers;

    @Data
    public static class ManagedUser {
        private Long userId;
        private String username;
        private String relation;
    }
}
