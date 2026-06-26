package com.xikang.physician.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Internal API token for Dify workflow HTTP callbacks (e.g. disease search).
 */
@Component
@ConfigurationProperties(prefix = "xikang.internal.ai")
public class InternalAiProperties {

    /** Bearer token shared with Dify HTTP node; empty disables internal endpoints. */
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
