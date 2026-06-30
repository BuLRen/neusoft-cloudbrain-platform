package com.xikang.physician.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Internal API token for Dify Agent custom-tool HTTP callbacks.
 */
@Component
@ConfigurationProperties(prefix = "xikang.internal.ai")
public class InternalAiProperties {

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
