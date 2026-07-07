package com.xikang.medtech.critical;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CriticalDetectResult {

    private boolean suspected;
    private String severity = "CRITICAL";
    private String detectSource = "rule";
    private List<CriticalItemHit> items = new ArrayList<>();

    public static CriticalDetectResult none() {
        CriticalDetectResult result = new CriticalDetectResult();
        result.setSuspected(false);
        return result;
    }

    public static CriticalDetectResult of(List<CriticalItemHit> items, String severity, String source) {
        CriticalDetectResult result = new CriticalDetectResult();
        result.setSuspected(!items.isEmpty());
        result.setItems(items);
        result.setSeverity(severity != null ? severity : "CRITICAL");
        result.setDetectSource(source != null ? source : "rule");
        return result;
    }
}
