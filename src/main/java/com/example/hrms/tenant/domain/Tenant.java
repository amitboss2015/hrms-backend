package com.example.hrms.tenant.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tenant entity representing a customer/organization in the multi-tenant SaaS.
 * Each tenant has isolated data identified by tenantId across all tables.
 */
@Entity
@Table(name = "tenant", indexes = {
    @Index(name = "idx_tenant_subdomain", columnList = "subdomain"),
    @Index(name = "idx_tenant_active", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {
    
    @Id
    @Column(length = 50)
    private String id;  // e.g., 'SASA001', 'TECH001'
    
    @Column(nullable = false, length = 255)
    private String name;  // e.g., 'Sasa Collection Pvt Ltd'
    
    @Column(unique = true, nullable = false, length = 100)
    private String subdomain;  // e.g., 'sasacollection' (used in URL: sasacollection.hrms.in)
    
    @Column(length = 255)
    private String customDomain;  // Optional custom domain
    
    // Contact Information
    @Column(nullable = false, length = 255)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(length = 100)
    private String city;
    
    @Column(length = 100)
    private String state;
    
    @Column(length = 100)
    @Builder.Default
    private String country = "India";
    
    @Column(length = 10)
    private String pincode;
    
    // Subscription Information
    @Column(length = 50)
    @Builder.Default
    private String plan = "FREE";  // FREE, BASIC, PRO, ENTERPRISE
    
    @Builder.Default
    private Integer maxEmployees = 10;
    
    private LocalDate subscriptionStart;
    
    private LocalDate subscriptionEnd;
    
    @Builder.Default
    private Boolean isActive = true;
    
    // Branding
    @Column(length = 500)
    private String logoUrl;
    
    @Column(length = 10)
    @Builder.Default
    private String primaryColor = "#10B981";  // Emerald color
    
    @Column(length = 10)
    private String secondaryColor;
    
    // Settings
    @Column(length = 50)
    @Builder.Default
    private String timezone = "Asia/Kolkata";
    
    @Column(length = 10)
    @Builder.Default
    private String dateFormat = "dd/MM/yyyy";
    
    @Column(length = 10)
    @Builder.Default
    private String currency = "INR";
    
    // Soft Delete Fields
    @Builder.Default
    private Boolean deleted = false;
    
    private LocalDateTime deletedAt;
    
    @Column(length = 100)
    private String deletedBy;  // Email of admin who deleted
    
    @Column(length = 500)
    private String deleteReason;
    
    // Audit
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if tenant subscription is valid
     */
    public boolean isSubscriptionValid() {
        if (!isActive) return false;
        if (subscriptionEnd == null) return true; // No end date = perpetual
        return LocalDate.now().isBefore(subscriptionEnd) || LocalDate.now().isEqual(subscriptionEnd);
    }
}
