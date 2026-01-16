package com.example.hrms.tenant.controller;

import com.example.hrms.tenant.TenantContext;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for tenant management.
 * These endpoints are typically for super admin / system operations.
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * Get current tenant info (based on request context)
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentTenant() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return tenantService.getTenant(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all tenants (super admin only)
     */
    @GetMapping
    public List<Tenant> getAllTenants() {
        return tenantService.getAllTenants();
    }

    /**
     * Get active tenants only
     */
    @GetMapping("/active")
    public List<Tenant> getActiveTenants() {
        return tenantService.getAllActiveTenants();
    }

    /**
     * Get tenant by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Tenant> getTenant(@PathVariable String id) {
        return tenantService.getTenant(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get tenant by subdomain
     */
    @GetMapping("/subdomain/{subdomain}")
    public ResponseEntity<Tenant> getTenantBySubdomain(@PathVariable String subdomain) {
        return tenantService.getTenantBySubdomain(subdomain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new tenant
     */
    @PostMapping
    public ResponseEntity<?> createTenant(@RequestBody Tenant tenant) {
        try {
            Tenant created = tenantService.createTenant(tenant);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update tenant
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable String id, @RequestBody Tenant updates) {
        try {
            Tenant updated = tenantService.updateTenant(id, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Activate or deactivate tenant
     */
    @PutMapping("/{id}/active")
    public ResponseEntity<?> setTenantActive(@PathVariable String id, 
                                              @RequestParam boolean active) {
        try {
            Tenant tenant = tenantService.setTenantActive(id, active);
            return ResponseEntity.ok(tenant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update subscription
     */
    @PutMapping("/{id}/subscription")
    public ResponseEntity<?> updateSubscription(@PathVariable String id,
                                                 @RequestParam String plan,
                                                 @RequestParam(required = false) LocalDate endDate) {
        try {
            Tenant tenant = tenantService.updateSubscription(id, plan, endDate);
            return ResponseEntity.ok(tenant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if tenant can add more employees
     */
    @GetMapping("/{id}/can-add-employee")
    public ResponseEntity<Map<String, Boolean>> canAddEmployee(@PathVariable String id) {
        boolean canAdd = tenantService.canAddEmployee(id);
        return ResponseEntity.ok(Map.of("canAdd", canAdd));
    }

    /**
     * Delete tenant (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
