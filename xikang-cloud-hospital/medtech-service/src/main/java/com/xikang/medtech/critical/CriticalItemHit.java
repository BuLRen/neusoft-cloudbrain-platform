package com.xikang.medtech.critical;

import lombok.Data;

@Data
public class CriticalItemHit {

    private String itemName;
    private String fieldKey;
    private String value;
    private String unit;
    private String referenceRange;
    private String rule;
    private String severity;
    private String reason;
}
