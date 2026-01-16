package com.example.hrms.repo;

import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyOffConfigRepository extends JpaRepository<WeeklyOffConfig, Long> {
    
    // ============ TENANT-AWARE METHODS ============
    
    /**
     * Find weekly off config by tenant and employment type
     */
    Optional<WeeklyOffConfig> findByTenantIdAndEmploymentTypeAndActiveTrue(
        String tenantId, EmploymentType employmentType);
    
    /**
     * Find all active weekly off configs for tenant
     */
    List<WeeklyOffConfig> findByTenantIdAndActiveTrue(String tenantId);
    
    /**
     * Find all weekly off configs for tenant
     */
    List<WeeklyOffConfig> findByTenantId(String tenantId);
    
    // Legacy methods using orgId (for backward compatibility)
    @Deprecated
    default Optional<WeeklyOffConfig> findByOrgIdAndEmploymentTypeAndActiveTrue(
            String orgId, EmploymentType employmentType) {
        return findByTenantIdAndEmploymentTypeAndActiveTrue(orgId, employmentType);
    }
    
    @Deprecated
    default List<WeeklyOffConfig> findByOrgIdAndActiveTrue(String orgId) {
        return findByTenantIdAndActiveTrue(orgId);
    }
}
