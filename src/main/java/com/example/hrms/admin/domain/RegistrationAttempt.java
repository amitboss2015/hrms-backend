package com.example.hrms.admin.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks registration attempts for rate limiting and fraud detection
 */
@Entity
@Table(name = "registration_attempts", indexes = {
    @Index(name = "idx_reg_ip", columnList = "ip_address"),
    @Index(name = "idx_reg_email", columnList = "email"),
    @Index(name = "idx_reg_time", columnList = "attempt_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "attempt_time", nullable = false)
    private LocalDateTime attemptTime;

    @Column(name = "is_successful")
    private Boolean isSuccessful = false;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "is_blocked")
    private Boolean isBlocked = false;

    @Column(name = "block_reason")
    private String blockReason;

    @PrePersist
    protected void onCreate() {
        if (attemptTime == null) {
            attemptTime = LocalDateTime.now();
        }
    }
}
