package com.example.hrms.payroll.repo;

import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalaryOvertimeConfigRepository extends JpaRepository<SalaryOvertimeConfig, Long> {
    
    /**
     * Find configuration for a specific tenant.
     */
    Optional<SalaryOvertimeConfig> findByTenantIdAndActiveTrue(String tenantId);
    
    /**
     * Find configuration by tenant ID (including inactive).
     */
    Optional<SalaryOvertimeConfig> findByTenantId(String tenantId);
    
    /**
     * Check if configuration exists for tenant.
     */
    boolean existsByTenantId(String tenantId);
}
