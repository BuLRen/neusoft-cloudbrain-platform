package com.xikang.registration.util;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonnelExcelSupportTest {

    @Test
    void physicianTemplateHasExpectedHeaders() throws Exception {
        byte[] bytes = PersonnelExcelWriter.writePhysicianTemplate(List.of(), List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var row = sheet.getRow(0);
            assertEquals("姓名*", row.getCell(0).getStringCellValue());
            assertEquals("科室*", row.getCell(1).getStringCellValue());
            assertEquals("挂号级别*", row.getCell(2).getStringCellValue());
        }
    }

    @Test
    void medtechTemplateHasExpectedHeaders() throws Exception {
        byte[] bytes = PersonnelExcelWriter.writeMedtechTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var row = sheet.getRow(0);
            assertEquals("姓名*", row.getCell(0).getStringCellValue());
            assertEquals("医技科室*", row.getCell(1).getStringCellValue());
        }
    }

    @Test
    void followUpTemplateHasExpectedHeaders() throws Exception {
        byte[] bytes = PersonnelExcelWriter.writeFollowUpTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var row = sheet.getRow(0);
            assertEquals("姓名*", row.getCell(0).getStringCellValue());
            assertEquals("临床科室*", row.getCell(1).getStringCellValue());
        }
    }

    @Test
    void attachmentUsesAsciiAndUtf8Filename() {
        ResponseEntity<byte[]> response = PersonnelExcelHttpSupport.attachment(
            new byte[] { 1, 2, 3 },
            "physicians_20260626.xlsx",
            "诊疗医生_20260626.xlsx"
        );
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.contains("filename=\"physicians_20260626.xlsx\""));
        assertTrue(disposition.contains("filename*=UTF-8''"));
        assertEquals(3, response.getBody().length);
    }
}
