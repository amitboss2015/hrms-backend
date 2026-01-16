package com.example.hrms.auth.security;

import com.example.hrms.auth.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider - handles token creation and validation
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret:hrms-super-secret-key-that-is-at-least-256-bits-long-for-hs512}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms:900000}")  // 15 minutes
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")  // 7 days
    private long refreshTokenExpiryMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // Ensure key is at least 512 bits for HS512
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            // Pad the key if too short
            byte[] paddedKey = new byte[64];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate access token (short-lived, contains user info)
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("tenantId", user.getTenantId())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Generate refresh token (long-lived, just a UUID)
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get refresh token expiry time
     */
    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    /**
     * Hash refresh token for storage (don't store plain tokens)
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Get user ID from token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Get tenant ID from token
     */
    public String getTenantIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("tenantId", String.class);
    }

    /**
     * Get email from token
     */
    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    /**
     * Get role from token
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Parse token claims
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get token expiry date
     */
    public Date getExpiryFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }
}
