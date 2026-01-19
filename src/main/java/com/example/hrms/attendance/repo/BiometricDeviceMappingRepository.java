package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.BiometricDeviceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiometricDeviceMappingRepository extends JpaRepository<BiometricDeviceMapping, Long> {
    
    /**
     * Find mapping by device ID and device employee code.
     * This is the primary lookup used during attendance import.
     */
    @Query("SELECT m FROM BiometricDeviceMapping m " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.device.id = :deviceId " +
           "AND m.deviceEmpCode = :deviceEmpCode " +
           "AND m.isActive = true")
    Optional<BiometricDeviceMapping> findActiveMapping(
        @Param("tenantId") String tenantId,
        @Param("deviceId") Long deviceId,
        @Param("deviceEmpCode") String deviceEmpCode
    );
    
    /**
     * Find mapping by device code (string) and device employee code.
     * Convenience method that joins with device table.
     */
    @Query("SELECT m FROM BiometricDeviceMapping m " +
           "JOIN m.device d " +
           "WHERE m.tenantId = :tenantId " +
           "AND d.deviceCode = :deviceCode " +
           "AND m.deviceEmpCode = :deviceEmpCode " +
           "AND m.isActive = true")
    Optional<BiometricDeviceMapping> findByDeviceCodeAndEmpCode(
        @Param("tenantId") String tenantId,
        @Param("deviceCode") String deviceCode,
        @Param("deviceEmpCode") String deviceEmpCode
    );
    
    /**
     * Find all mappings for a device
     */
    List<BiometricDeviceMapping> findByDeviceIdOrderByDeviceEmpCodeAsc(Long deviceId);
    
    /**
     * Find all mappings for an employee
     */
    List<BiometricDeviceMapping> findByEmployeeIdOrderByDeviceEmpCodeAsc(Long employeeId);
    
    /**
     * Find all mappings for a tenant
     */
    List<BiometricDeviceMapping> findByTenantIdOrderByDeviceEmpCodeAsc(String tenantId);
    
    /**
     * Find mappings by tenant and device
     */
    List<BiometricDeviceMapping> findByTenantIdAndDeviceIdOrderByDeviceEmpCodeAsc(String tenantId, Long deviceId);
    
    /**
     * Check if a mapping exists for a device and employee code
     */
    boolean existsByTenantIdAndDeviceIdAndDeviceEmpCode(String tenantId, Long deviceId, String deviceEmpCode);
    
    /**
     * Check if an employee is already mapped to a device
     */
    boolean existsByDeviceIdAndEmployeeId(Long deviceId, Long employeeId);
    
    /**
     * Count mappings for a device
     */
    long countByDeviceId(Long deviceId);
    
    /**
     * Delete all mappings for a device
     */
    void deleteByDeviceId(Long deviceId);
    
    /**
     * Delete all mappings for an employee
     */
    void deleteByEmployeeId(Long employeeId);
    
    /**
     * Find all active mappings for a tenant (for bulk operations)
     */
    @Query("SELECT m FROM BiometricDeviceMapping m " +
           "JOIN FETCH m.device d " +
           "JOIN FETCH m.employee e " +
           "WHERE m.tenantId = :tenantId AND m.isActive = true")
    List<BiometricDeviceMapping> findAllActiveWithDetails(@Param("tenantId") String tenantId);
}
