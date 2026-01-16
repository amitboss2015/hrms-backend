package com.example.hrms.tenant.repo;

import com.example.hrms.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    
    /**
     * Find tenant by subdomain (used for URL-based tenant resolution)
     */
    Optional<Tenant> findBySubdomain(String subdomain);
    
    /**
     * Find tenant by custom domain
     */
    Optional<Tenant> findByCustomDomain(String customDomain);
    
    /**
     * Find all active tenants
     */
    List<Tenant> findByIsActiveTrue();
    
    /**
     * Find tenants by plan
     */
    List<Tenant> findByPlan(String plan);
    
    /**
     * Check if subdomain is available
     */
    boolean existsBySubdomain(String subdomain);
    
    /**
     * Find active tenant by subdomain
     */
    @Query("SELECT t FROM Tenant t WHERE t.subdomain = :subdomain AND t.isActive = true")
    Optional<Tenant> findActiveBySubdomain(@Param("subdomain") String subdomain);
    
    /**
     * Count employees for a tenant (used for plan limits)
     */
    @Query(value = "SELECT COUNT(*) FROM employee WHERE tenant_id = :tenantId AND status = 'ACTIVE'", 
           nativeQuery = true)
    long countActiveEmployees(@Param("tenantId") String tenantId);
}
