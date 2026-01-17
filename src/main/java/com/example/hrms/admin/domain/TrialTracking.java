package com.example.hrms.admin.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks trial period for each tenant/company
 */
@Entity
@Table(name = "trial_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 50)
    private String tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column(name = "admin_phone")
    private String adminPhone;

    @Column(name = "trial_start_date", nullable = false)
    private LocalDate trialStartDate;

    @Column(name = "trial_end_date", nullable = false)
    private LocalDate trialEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trial_status", nullable = false)
    private TrialStatus trialStatus = TrialStatus.ACTIVE;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "fraud_score")
    private Integer fraudScore = 0;

    @Column(name = "risk_level", length = 20)
    private String riskLevel = "LOW";

    @Column(name = "fraud_reasons", columnDefinition = "TEXT")
    private String fraudReasons;

    @Column(name = "is_email_verified")
    private Boolean isEmailVerified = false;

    @Column(name = "is_phone_verified")
    private Boolean isPhoneVerified = false;

    @Column(name = "employee_count")
    private Integer employeeCount = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "is_suspended")
    private Boolean isSuspended = false;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_by")
    private String suspendedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (trialStartDate == null) {
            trialStartDate = LocalDate.now();
        }
        if (trialEndDate == null) {
            trialEndDate = trialStartDate.plusDays(14); // 14-day trial
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public boolean isTrialExpired() {
        return LocalDate.now().isAfter(trialEndDate);
    }

    public long getDaysRemaining() {
        if (isTrialExpired()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), trialEndDate);
    }

    public long getTrialDaysUsed() {
        return java.time.temporal.ChronoUnit.DAYS.between(trialStartDate, LocalDate.now());
    }

    public enum TrialStatus {
        PENDING,      // Registration submitted, awaiting email verification
        ACTIVE,       // Trial is active
        EXPIRED,      // Trial period ended
        CONVERTED,    // Converted to paid plan
        SUSPENDED,    // Suspended by admin
        CANCELLED     // User cancelled
    }
}
