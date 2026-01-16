package com.example.hrms.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh token for JWT token refresh.
 * Stored in DB for revocation capability.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_hash", columnList = "tokenHash"),
    @Index(name = "idx_refresh_user", columnList = "userId"),
    @Index(name = "idx_refresh_expiry", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;  // SHA-256 hash of actual token

    @Column(length = 500)
    private String deviceInfo;  // User-Agent

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    private Boolean revoked = false;

    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(revoked) && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }
}
