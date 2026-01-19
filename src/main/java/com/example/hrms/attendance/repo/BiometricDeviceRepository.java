package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.BiometricDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice, Long> {
    
    /**
     * Find all devices for a tenant
     */
    List<BiometricDevice> findByTenantIdOrderByDeviceCodeAsc(String tenantId);
    
    /**
     * Find active devices for a tenant
     */
    List<BiometricDevice> findByTenantIdAndIsActiveTrueOrderByDeviceCodeAsc(String tenantId);
    
    /**
     * Find device by tenant and device code
     */
    Optional<BiometricDevice> findByTenantIdAndDeviceCode(String tenantId, String deviceCode);
    
    /**
     * Find the default device for a tenant
     */
    Optional<BiometricDevice> findByTenantIdAndIsDefaultTrue(String tenantId);
    
    /**
     * Check if a device code already exists for a tenant
     */
    boolean existsByTenantIdAndDeviceCode(String tenantId, String deviceCode);
    
    /**
     * Count devices for a tenant
     */
    long countByTenantId(String tenantId);
    
    /**
     * Find devices by location
     */
    List<BiometricDevice> findByTenantIdAndLocationContainingIgnoreCase(String tenantId, String location);
    
    /**
     * Get default device or create fallback query
     */
    @Query("SELECT d FROM BiometricDevice d WHERE d.tenantId = :tenantId AND d.isDefault = true")
    Optional<BiometricDevice> getDefaultDevice(@Param("tenantId") String tenantId);
}
