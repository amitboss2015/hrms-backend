package com.example.hrms.auth.domain;

import com.example.hrms.auth.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * User entity for authentication.
 * Links to Employee for employee-users.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_tenant", columnList = "tenantId")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_tenant_email", columnNames = {"tenantId", "email"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    // Link to employee record (optional - for employee self-service)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UserRole role = UserRole.EMPLOYEE;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isLocked = false;

    @Builder.Default
    private Integer failedAttempts = 0;

    private LocalDateTime lockTime;

    private LocalDateTime passwordChangedAt;

    // MFA (optional)
    @Builder.Default
    private Boolean mfaEnabled = false;

    private String mfaSecret;

    // Audit
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============ UserDetails Implementation ============

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (Boolean.TRUE.equals(isLocked) && lockTime != null) {
            // Auto-unlock after 15 minutes
            if (lockTime.plusMinutes(15).isBefore(LocalDateTime.now())) {
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(isActive);
    }

    // ============ Helpers ============

    public String getFullName() {
        if (firstName == null && lastName == null) return email;
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }

    public void incrementFailedAttempts() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;
        if (this.failedAttempts >= 5) {
            this.isLocked = true;
            this.lockTime = LocalDateTime.now();
        }
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.isLocked = false;
        this.lockTime = null;
    }

    public void recordLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
        resetFailedAttempts();
    }
}
