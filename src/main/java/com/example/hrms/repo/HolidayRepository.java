package com.example.hrms.repo;

import com.example.hrms.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    
    // ============ TENANT-AWARE METHODS ============
    // Note: tenantId replaces orgId - keeping method names with "tenantId"
    
    /**
     * Find holidays by tenant and year
     */
    List<Holiday> findByTenantIdAndYearAndActiveTrue(String tenantId, Integer year);
    
    /**
     * Find holidays between dates for tenant
     */
    List<Holiday> findByTenantIdAndHolidayDateBetweenAndActiveTrue(
        String tenantId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find all active holidays for tenant
     */
    List<Holiday> findByTenantIdAndActiveTrue(String tenantId);
    
    /**
     * Find all holidays for tenant
     */
    List<Holiday> findByTenantId(String tenantId);
    
    /**
     * Check if holiday exists
     */
    boolean existsByTenantIdAndHolidayDateAndName(String tenantId, LocalDate date, String name);
    
    // Legacy methods using orgId (for backward compatibility during transition)
    @Deprecated
    default List<Holiday> findByOrgIdAndYearAndActiveTrue(String orgId, Integer year) {
        return findByTenantIdAndYearAndActiveTrue(orgId, year);
    }
    
    @Deprecated
    default List<Holiday> findByOrgIdAndHolidayDateBetweenAndActiveTrue(
            String orgId, LocalDate startDate, LocalDate endDate) {
        return findByTenantIdAndHolidayDateBetweenAndActiveTrue(orgId, startDate, endDate);
    }
    
    @Deprecated
    default List<Holiday> findByOrgIdAndActiveTrue(String orgId) {
        return findByTenantIdAndActiveTrue(orgId);
    }
    
    @Deprecated
    default boolean existsByOrgIdAndHolidayDateAndName(String orgId, LocalDate date, String name) {
        return existsByTenantIdAndHolidayDateAndName(orgId, date, name);
    }
}
