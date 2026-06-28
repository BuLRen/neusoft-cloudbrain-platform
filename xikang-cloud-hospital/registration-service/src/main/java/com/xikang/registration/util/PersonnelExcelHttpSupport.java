package com.xikang.registration.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

public final class PersonnelExcelHttpSupport {

    private PersonnelExcelHttpSupport() {
    }

    public static ResponseEntity<byte[]> attachment(byte[] body, String asciiFilename, String displayFilename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        String encodedDisplayName = java.net.URLEncoder.encode(displayFilename, StandardCharsets.UTF_8)
            .replace("+", "%20");
        headers.add(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + encodedDisplayName
        );
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
