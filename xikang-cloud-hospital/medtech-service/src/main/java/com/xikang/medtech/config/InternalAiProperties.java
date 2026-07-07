package com.xikang.medtech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xikang.internal.ai")
public class InternalAiProperties {

    private String token = "";

    public String getToken() {
        return token == null ? "" : token.trim();
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isEnabled() {
        return !getToken().isEmpty();
    }
}
