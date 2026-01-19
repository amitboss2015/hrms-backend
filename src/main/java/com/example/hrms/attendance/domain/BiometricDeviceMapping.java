package com.example.hrms.attendance.domain;

import com.example.hrms.domain.Employee;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Maps device-specific employee codes to HRMS employees.
 * This allows the same employee to have different codes in different biometric devices,
 * and allows the same code to refer to different employees in different devices.
 */
@Entity
@Table(name = "biometric_device_mappings",
    uniqueConstraints = {
        // Same device cannot have duplicate employee codes
        @UniqueConstraint(name = "uk_device_emp_code", columnNames = {"tenant_id", "device_id", "device_emp_code"})
    },
    indexes = {
        @Index(name = "idx_mapping_tenant", columnList = "tenant_id"),
        @Index(name = "idx_mapping_device", columnList = "device_id"),
        @Index(name = "idx_mapping_employee", columnList = "employee_id"),
        @Index(name = "idx_mapping_lookup", columnList = "tenant_id, device_id, device_emp_code")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BiometricDeviceMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;
    
    /**
     * Reference to the biometric device
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private BiometricDevice device;
    
    /**
     * The employee code as registered in THIS biometric device.
     * This may be different from the HRMS employee code.
     */
    @Column(name = "device_emp_code", nullable = false, length = 50)
    private String deviceEmpCode;
    
    /**
     * Reference to the HRMS employee
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    
    /**
     * Optional: Employee name as shown in the biometric device (for reference)
     */
    @Column(name = "device_emp_name", length = 100)
    private String deviceEmpName;
    
    /**
     * Whether this mapping is currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
