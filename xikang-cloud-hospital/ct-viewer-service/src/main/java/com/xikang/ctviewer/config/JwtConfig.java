package com.xikang.ctviewer.config;

import com.xikang.common.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    public JwtConfig(
        @Value("${jwt.secret:}") String secret,
        @Value("${jwt.defaultExpirationMs:86400000}") long defaultExpirationMs
    ) {
        JwtUtils.configure(secret, defaultExpirationMs);
    }
}
