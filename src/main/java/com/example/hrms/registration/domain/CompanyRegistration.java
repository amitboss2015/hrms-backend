package com.example.hrms.registration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores pending company registrations before email verification.
 * Once activated, creates Tenant and Admin User records.
 */
@Entity
@Table(name = "company_registrations",
       indexes = {
           @Index(name = "idx_reg_email", columnList = "email"),
           @Index(name = "idx_reg_token", columnList = "activation_token"),
           @Index(name = "idx_reg_subdomain", columnList = "subdomain")
       })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "admin_name", nullable = false)
    private String adminName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String subdomain;

    @Column(name = "activation_token", unique = true, length = 100)
    private String activationToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    @Builder.Default
    private Boolean activated = false;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    // Created tenant ID after activation
    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    // Fraud detection fields
    @Column(name = "fraud_score")
    @Builder.Default
    private Integer fraudScore = 0;

    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private String riskLevel = "LOW";

    /**
     * Generate a unique activation token
     */
    public void generateActivationToken() {
        this.activationToken = UUID.randomUUID().toString().replace("-", "");
        this.tokenExpiresAt = LocalDateTime.now().plusHours(24); // 24 hour expiry
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt == null || LocalDateTime.now().isAfter(tokenExpiresAt);
    }

    /**
     * Mark as activated
     */
    public void activate(String tenantId) {
        this.activated = true;
        this.activatedAt = LocalDateTime.now();
        this.tenantId = tenantId;
        this.activationToken = null; // Clear token after use
    }

    /**
     * Generate subdomain from company name
     */
    public static String generateSubdomain(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "company" + System.currentTimeMillis();
        }
        String subdomain = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]", "") // Remove special chars
                .trim();
        if (subdomain.length() > 20) {
            subdomain = subdomain.substring(0, 20);
        }
        if (subdomain.isEmpty()) {
            subdomain = "company" + System.currentTimeMillis();
        }
        return subdomain;
    }
}
