package com.example.hrms.repo;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.enums.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // Legacy methods (non-tenant-aware) - kept for backward compatibility
    Optional<Employee> findByEmpCode(String empCode);
    boolean existsByEmpCode(String empCode);
    void deleteByEmpCode(String empCode);
    
    // ============ TENANT-AWARE METHODS ============
    
    /**
     * Find employee by tenant and employee code
     */
    Optional<Employee> findByTenantIdAndEmpCode(String tenantId, String empCode);
    
    /**
     * Check if employee code exists for tenant
     */
    boolean existsByTenantIdAndEmpCode(String tenantId, String empCode);
    
    /**
     * Delete by tenant and employee code
     */
    void deleteByTenantIdAndEmpCode(String tenantId, String empCode);
    
    /**
     * Find all employees for a tenant
     */
    List<Employee> findByTenantId(String tenantId);
    
    /**
     * Find all active employees for a tenant
     */
    List<Employee> findByTenantIdAndStatus(String tenantId, EmployeeStatus status);
    
    /**
     * Find employee by ID and tenant (security check)
     */
    Optional<Employee> findByIdAndTenantId(Long id, String tenantId);
    
    /**
     * Count employees by tenant
     */
    long countByTenantId(String tenantId);
    
    /**
     * Count active employees by tenant
     */
    long countByTenantIdAndStatus(String tenantId, EmployeeStatus status);
    
    /**
     * Search employees by name within tenant
     */
    @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND " +
           "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Employee> searchByTenantId(@Param("tenantId") String tenantId, @Param("search") String search);
}
