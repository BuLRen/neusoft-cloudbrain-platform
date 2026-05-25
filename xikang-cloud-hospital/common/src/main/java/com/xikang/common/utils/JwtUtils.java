package com.xikang.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility class
 */
@Slf4j
public class JwtUtils {

    private static final String SECRET_KEY = "xikang-cloud-hospital-secret-key-for-jwt-token-generation";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    /**
     * Generate JWT token
     */
    public static String generateToken(String subject) {
        return generateToken(subject, new HashMap<>());
    }

    /**
     * Generate JWT token with extra claims
     */
    public static String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(KEY)
                .compact();
    }

    /**
     * Parse JWT token and get claims
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Failed to parse JWT token", e);
            return null;
        }
    }

    /**
     * Get subject from token
     */
    public static String getSubject(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Check if token is expired
     */
    public static boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return true;
        }
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    /**
     * Validate token
     */
    public static boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("JWT token validation failed", e);
            return false;
        }
    }

    private JwtUtils() {
        // Utility class, prevent instantiation
    }
}
