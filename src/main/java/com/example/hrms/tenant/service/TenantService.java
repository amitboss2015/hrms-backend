package com.example.hrms.tenant.service;

import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for tenant management operations
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {
    
    private final TenantRepository tenantRepository;
    
    /**
     * Create a new tenant
     */
    public Tenant createTenant(Tenant tenant) {
        // Validate subdomain is unique
        if (tenantRepository.existsBySubdomain(tenant.getSubdomain())) {
            throw new IllegalArgumentException("Subdomain already exists: " + tenant.getSubdomain());
        }
        
        // Set defaults
        if (tenant.getPlan() == null) {
            tenant.setPlan("FREE");
        }
        if (tenant.getMaxEmployees() == null) {
            tenant.setMaxEmployees(getMaxEmployeesForPlan(tenant.getPlan()));
        }
        if (tenant.getSubscriptionStart() == null) {
            tenant.setSubscriptionStart(LocalDate.now());
        }
        
        return tenantRepository.save(tenant);
    }
    
    /**
     * Get tenant by ID
     */
    public Optional<Tenant> getTenant(String tenantId) {
        return tenantRepository.findById(tenantId);
    }
    
    /**
     * Get tenant by subdomain
     */
    public Optional<Tenant> getTenantBySubdomain(String subdomain) {
        return tenantRepository.findBySubdomain(subdomain);
    }
    
    /**
     * Get all active tenants
     */
    public List<Tenant> getAllActiveTenants() {
        return tenantRepository.findByIsActiveTrue();
    }
    
    /**
     * Get all tenants
     */
    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }
    
    /**
     * Update tenant
     */
    public Tenant updateTenant(String tenantId, Tenant updates) {
        Tenant existing = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        
        // Update fields
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
        if (updates.getPhone() != null) existing.setPhone(updates.getPhone());
        if (updates.getAddress() != null) existing.setAddress(updates.getAddress());
        if (updates.getCity() != null) existing.setCity(updates.getCity());
        if (updates.getState() != null) existing.setState(updates.getState());
        if (updates.getPincode() != null) existing.setPincode(updates.getPincode());
        if (updates.getPlan() != null) existing.setPlan(updates.getPlan());
        if (updates.getMaxEmployees() != null) existing.setMaxEmployees(updates.getMaxEmployees());
        if (updates.getLogoUrl() != null) existing.setLogoUrl(updates.getLogoUrl());
        if (updates.getPrimaryColor() != null) existing.setPrimaryColor(updates.getPrimaryColor());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        
        return tenantRepository.save(existing);
    }
    
    /**
     * Activate/Deactivate tenant
     */
    public Tenant setTenantActive(String tenantId, boolean active) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        tenant.setIsActive(active);
        return tenantRepository.save(tenant);
    }
    
    /**
     * Update subscription
     */
    public Tenant updateSubscription(String tenantId, String plan, LocalDate endDate) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        
        tenant.setPlan(plan);
        tenant.setMaxEmployees(getMaxEmployeesForPlan(plan));
        tenant.setSubscriptionEnd(endDate);
        
        return tenantRepository.save(tenant);
    }
    
    /**
     * Check if tenant can add more employees
     */
    public boolean canAddEmployee(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return false;
        
        long currentCount = tenantRepository.countActiveEmployees(tenantId);
        return currentCount < tenant.getMaxEmployees();
    }
    
    /**
     * Get max employees for plan
     */
    private int getMaxEmployeesForPlan(String plan) {
        return switch (plan.toUpperCase()) {
            case "FREE" -> 10;
            case "BASIC" -> 50;
            case "PRO" -> 200;
            case "ENTERPRISE" -> 10000;
            default -> 10;
        };
    }
    
    /**
     * Delete tenant (soft delete - just deactivate)
     */
    public void deleteTenant(String tenantId) {
        setTenantActive(tenantId, false);
    }
}
