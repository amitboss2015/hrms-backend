package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Represents a biometric device (attendance machine) registered in the system.
 * Each device can have its own employee code mappings.
 */
@Entity
@Table(name = "biometric_devices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_device_code", columnNames = {"tenant_id", "device_code"})
    },
    indexes = {
        @Index(name = "idx_device_tenant", columnList = "tenant_id"),
        @Index(name = "idx_device_active", columnList = "tenant_id, is_active")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BiometricDevice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;
    
    @Column(name = "org_id")
    private Long orgId;
    
    /**
     * Unique code for this device within the tenant (e.g., "MUMBAI_GATE_1", "DELHI_MAIN")
     */
    @Column(name = "device_code", nullable = false, length = 50)
    private String deviceCode;
    
    /**
     * Human-readable name for the device
     */
    @Column(name = "device_name", length = 100)
    private String deviceName;
    
    /**
     * Location/branch where this device is installed
     */
    @Column(name = "location", length = 100)
    private String location;
    
    /**
     * Optional description or notes
     */
    @Column(name = "description", length = 500)
    private String description;
    
    /**
     * Device serial number or hardware ID (optional)
     */
    @Column(name = "serial_number", length = 100)
    private String serialNumber;
    
    /**
     * Whether this device is currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * Whether this is the default device for the tenant.
     * When importing without specifying a device, the default device is used.
     * This maintains backward compatibility with single-device setups.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;
    
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
