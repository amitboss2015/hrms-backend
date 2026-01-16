package com.example.hrms.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit log for all authentication events
 */
@Entity
@Table(name = "login_audit", indexes = {
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_tenant", columnList = "tenantId"),
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 50)
    private String tenantId;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 50)
    private String action;  // LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, TOKEN_REFRESH, PASSWORD_CHANGE

    @Column(length = 255)
    private String failureReason;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Factory methods
    public static LoginAudit loginSuccess(Long userId, String tenantId, String email, String ip, String userAgent) {
        return LoginAudit.builder()
                .userId(userId)
                .tenantId(tenantId)
                .email(email)
                .ipAddress(ip)
                .userAgent(userAgent)
                .action("LOGIN_SUCCESS")
                .build();
    }

    public static LoginAudit loginFailed(String tenantId, String email, String ip, String userAgent, String reason) {
        return LoginAudit.builder()
                .tenantId(tenantId)
                .email(email)
                .ipAddress(ip)
                .userAgent(userAgent)
                .action("LOGIN_FAILED")
                .failureReason(reason)
                .build();
    }

    public static LoginAudit logout(Long userId, String tenantId, String email, String ip) {
        return LoginAudit.builder()
                .userId(userId)
                .tenantId(tenantId)
                .email(email)
                .ipAddress(ip)
                .action("LOGOUT")
                .build();
    }

    public static LoginAudit tokenRefresh(Long userId, String tenantId, String ip) {
        return LoginAudit.builder()
                .userId(userId)
                .tenantId(tenantId)
                .ipAddress(ip)
                .action("TOKEN_REFRESH")
                .build();
    }
}
