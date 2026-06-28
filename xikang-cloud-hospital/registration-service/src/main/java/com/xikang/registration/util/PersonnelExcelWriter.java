package com.xikang.registration.util;

import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.RegistLevel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class PersonnelExcelWriter {

    private PersonnelExcelWriter() {
    }

    public static byte[] writePhysicianTemplate(List<Department> departments, List<RegistLevel> registLevels) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet("导入数据");
            writeHeaderRow(workbook, dataSheet, new String[] { "姓名*", "科室*", "挂号级别*" });
            writeExampleRow(dataSheet, new String[] { "张三", "内科", "专家号" });

            Sheet dictSheet = workbook.createSheet("字典说明");
            writeHeaderRow(workbook, dictSheet, new String[] { "临床科室", "挂号级别" });
            int maxRows = Math.max(departments.size(), registLevels.size());
            for (int i = 0; i < maxRows; i++) {
                Row row = dictSheet.createRow(i + 1);
                if (i < departments.size()) {
                    row.createCell(0).setCellValue(departments.get(i).getName());
                }
                if (i < registLevels.size()) {
                    row.createCell(1).setCellValue(registLevels.get(i).getName());
                }
            }
            autosize(dataSheet, 3);
            autosize(dictSheet, 2);
            return toBytes(workbook);
        } catch (IOException ex) {
            throw new IllegalStateException("生成 Excel 失败", ex);
        }
    }

    public static byte[] writeMedtechTemplate(List<Department> departments) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet("导入数据");
            writeHeaderRow(workbook, dataSheet, new String[] { "姓名*", "医技科室*" });
            writeExampleRow(dataSheet, new String[] { "李四", "检验科" });

            Sheet dictSheet = workbook.createSheet("字典说明");
            writeHeaderRow(workbook, dictSheet, new String[] { "医技科室" });
            for (int i = 0; i < departments.size(); i++) {
                Row row = dictSheet.createRow(i + 1);
                row.createCell(0).setCellValue(departments.get(i).getName());
            }
            autosize(dataSheet, 2);
            autosize(dictSheet, 1);
            return toBytes(workbook);
        } catch (IOException ex) {
            throw new IllegalStateException("生成 Excel 失败", ex);
        }
    }

    public static byte[] writePhysicianExport(List<String[]> rows) {
        return writeExport(new String[] {
            "员工ID", "姓名", "科室", "挂号级别", "档案状态", "登录账号", "账号状态"
        }, rows);
    }

    public static byte[] writeMedtechExport(List<String[]> rows) {
        return writeExport(new String[] {
            "员工ID", "姓名", "医技科室", "档案状态", "登录账号", "账号状态"
        }, rows);
    }

    private static byte[] writeExport(String[] headers, List<String[]> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("导出数据");
            writeHeaderRow(workbook, sheet, headers);
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String[] values = rows.get(i);
                for (int j = 0; j < values.length; j++) {
                    row.createCell(j).setCellValue(values[j] == null ? "" : values[j]);
                }
            }
            autosize(sheet, headers.length);
            return toBytes(workbook);
        } catch (IOException ex) {
            throw new IllegalStateException("生成 Excel 失败", ex);
        }
    }

    private static void writeHeaderRow(Workbook workbook, Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void writeExampleRow(Sheet sheet, String[] values) {
        Row row = sheet.createRow(1);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private static void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
