package com.xikang.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility class
 */
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);
    private static final String DEFAULT_SECRET_KEY = "xikang-cloud-hospital-secret-key-for-jwt-token-generation";
    private static final long DEFAULT_EXPIRATION_TIME = 86400000; // 24 hours

    private static volatile SecretKey key = Keys.hmacShaKeyFor(DEFAULT_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    private static volatile long expirationTimeMs = DEFAULT_EXPIRATION_TIME;

    /**
     * Configure secret and default expiration at runtime (typically via Spring config).
     */
    public static synchronized void configure(String secretKey, long expirationTimeMs) {
        if (secretKey != null && !secretKey.isBlank()) {
            JwtUtils.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }
        if (expirationTimeMs > 0) {
            JwtUtils.expirationTimeMs = expirationTimeMs;
        }
    }

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
        return generateToken(subject, claims, expirationTimeMs);
    }

    /**
     * Generate JWT token with extra claims and custom ttl (milliseconds).
     */
    public static String generateToken(String subject, Map<String, Object> claims, long ttlMs) {
        Date now = new Date();
        long effectiveTtlMs = ttlMs > 0 ? ttlMs : expirationTimeMs;
        Date expiration = new Date(now.getTime() + effectiveTtlMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * Parse JWT token and get claims
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
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
                    .verifyWith(key)
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
