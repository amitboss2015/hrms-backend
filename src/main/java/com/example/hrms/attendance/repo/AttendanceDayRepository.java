// AttendanceDayRepository.java
package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {
    
    // Legacy methods (non-tenant-aware)
    List<AttendanceDay> findByWorkDateBetween(LocalDate from, LocalDate to);
    List<AttendanceDay> findByEmployeeIdAndWorkDateBetween(Long empId, LocalDate from, LocalDate to);
    List<AttendanceDay> findByOrgIdAndWorkDateBetween(Long orgId, LocalDate from, LocalDate to);
    void deleteByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    
    @Modifying
    @Query("DELETE FROM AttendanceDay d WHERE d.orgId = :orgId AND d.workDate >= :fromDate AND d.workDate <= :toDate")
    void deleteByOrgIdAndWorkDateBetween(@Param("orgId") Long orgId, 
                                          @Param("fromDate") LocalDate fromDate, 
                                          @Param("toDate") LocalDate toDate);
    
    // ============ TENANT-AWARE METHODS ============
    
    /**
     * Find attendance days by tenant and date range
     */
    List<AttendanceDay> findByTenantIdAndWorkDateBetween(String tenantId, LocalDate from, LocalDate to);
    
    /**
     * Find attendance days by tenant, employee and date range
     */
    List<AttendanceDay> findByTenantIdAndEmployeeIdAndWorkDateBetween(
        String tenantId, Long empId, LocalDate from, LocalDate to);
    
    /**
     * Find attendance days for an employee on a specific date
     */
    List<AttendanceDay> findByTenantIdAndEmployeeIdAndWorkDate(
        String tenantId, Long empId, LocalDate workDate);
    
    /**
     * Delete by tenant, employee and date
     */
    @Modifying
    @Query("DELETE FROM AttendanceDay d WHERE d.tenantId = :tenantId AND d.employeeId = :empId AND d.workDate = :workDate")
    void deleteByTenantIdAndEmployeeIdAndWorkDate(
        @Param("tenantId") String tenantId, 
        @Param("empId") Long empId, 
        @Param("workDate") LocalDate workDate);
    
    /**
     * Delete all days for a tenant and date range
     */
    @Modifying
    @Query("DELETE FROM AttendanceDay d WHERE d.tenantId = :tenantId AND d.workDate >= :fromDate AND d.workDate <= :toDate")
    void deleteByTenantIdAndWorkDateBetween(
        @Param("tenantId") String tenantId, 
        @Param("fromDate") LocalDate fromDate, 
        @Param("toDate") LocalDate toDate);
    
    /**
     * Count attendance days by tenant and date range
     */
    long countByTenantIdAndWorkDateBetween(String tenantId, LocalDate from, LocalDate to);
    
    /**
     * Check if attendance exists for tenant, employee and date
     */
    boolean existsByTenantIdAndEmployeeIdAndWorkDate(String tenantId, Long empId, LocalDate workDate);
    
    /**
     * Find by tenant, employee ID and work date (single entry)
     */
    java.util.Optional<AttendanceDay> findByTenantIdAndEmployeeIdAndWorkDateAndShiftCodes(
        String tenantId, Long empId, LocalDate workDate, String shiftCodes);
    
    /**
     * Find single attendance entry
     */
    java.util.Optional<AttendanceDay> findFirstByTenantIdAndEmployeeIdAndWorkDate(
        String tenantId, Long empId, LocalDate workDate);
}
