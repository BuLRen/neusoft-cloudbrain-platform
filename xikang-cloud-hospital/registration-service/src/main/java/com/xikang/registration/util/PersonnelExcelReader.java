package com.xikang.registration.util;

import com.xikang.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PersonnelExcelReader {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private PersonnelExcelReader() {
    }

    public static List<Map<String, String>> readDataRows(InputStream inputStream, List<String> expectedHeaders) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BusinessException(400, "Excel 文件为空");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(400, "Excel 缺少表头行");
            }
            Map<Integer, String> columnIndex = mapHeaderColumns(headerRow, expectedHeaders);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, columnIndex.keySet())) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : columnIndex.entrySet()) {
                    values.put(entry.getValue(), readCell(row.getCell(entry.getKey())));
                }
                rows.add(values);
            }
            if (rows.isEmpty()) {
                throw new BusinessException(400, "Excel 中没有可导入的数据行");
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(400, "无法解析 Excel 文件：" + ex.getMessage());
        }
    }

    private static Map<Integer, String> mapHeaderColumns(Row headerRow, List<String> expectedHeaders) {
        Map<Integer, String> columnIndex = new LinkedHashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String header = normalizeHeader(readCell(headerRow.getCell(cellIndex)));
            if (header.isEmpty()) {
                continue;
            }
            for (String expected : expectedHeaders) {
                if (header.equals(normalizeHeader(expected)) || header.startsWith(normalizeHeader(expected))) {
                    columnIndex.put(cellIndex, expected);
                    break;
                }
            }
        }
        for (String expected : expectedHeaders) {
            if (!columnIndex.containsValue(expected)) {
                throw new BusinessException(400, "缺少必填列：" + expected);
            }
        }
        return columnIndex;
    }

    private static boolean isBlankRow(Row row, Iterable<Integer> columnIndexes) {
        for (Integer index : columnIndexes) {
            if (!readCell(row.getCell(index)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.replace("*", "").trim();
    }
}
