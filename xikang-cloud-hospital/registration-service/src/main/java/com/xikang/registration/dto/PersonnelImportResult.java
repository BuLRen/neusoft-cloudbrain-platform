package com.xikang.registration.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PersonnelImportResult {
    private int totalRows;
    private int successCount;
    private int skippedCount;
    private int failedCount;
    private List<PersonnelImportRowResult> rows = new ArrayList<>();
}
