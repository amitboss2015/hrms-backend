package com.example.hrms.repo;

import com.example.hrms.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    
    // Legacy methods (non-tenant-aware) - kept for backward compatibility
    Optional<Shift> findByCode(String code);
    boolean existsByCode(String code);
    void deleteByCode(String code);
    
    // ============ TENANT-AWARE METHODS ============
    
    /**
     * Find shift by tenant and code
     */
    Optional<Shift> findByTenantIdAndCode(String tenantId, String code);
    
    /**
     * Check if shift code exists for tenant
     */
    boolean existsByTenantIdAndCode(String tenantId, String code);
    
    /**
     * Delete by tenant and shift code
     */
    void deleteByTenantIdAndCode(String tenantId, String code);
    
    /**
     * Find all shifts for a tenant
     */
    List<Shift> findByTenantId(String tenantId);
    
    /**
     * Find all active shifts for a tenant
     */
    List<Shift> findByTenantIdAndActiveTrue(String tenantId);
    
    /**
     * Find shift by ID and tenant (security check)
     */
    Optional<Shift> findByIdAndTenantId(Long id, String tenantId);
    
    /**
     * Count shifts by tenant
     */
    long countByTenantId(String tenantId);
}
