package com.xikang.ctviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ct-viewer.internal")
public class InternalServiceProperties {

    private String token = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public boolean isEnabled() {
        return token != null && !token.isBlank();
    }
}
