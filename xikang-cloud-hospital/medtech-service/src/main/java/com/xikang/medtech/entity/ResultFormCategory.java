package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultFormCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private String categoryCode;
    private String categoryName;
    private String description;
}
