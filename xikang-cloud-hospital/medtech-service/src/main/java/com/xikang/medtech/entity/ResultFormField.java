package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultFormField implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String ownerType;
    private String ownerKey;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private Boolean required;
    private Integer sortOrder;
    private String placeholder;
    private Integer maxLength;
    private String optionsJson;
}
