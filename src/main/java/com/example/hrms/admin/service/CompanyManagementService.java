package com.example.hrms.admin.service;

import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for Super Admin company management operations.
 * Handles soft delete, restore, and permanent deletion of companies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyManagementService {

    private final TenantRepository tenantRepo;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all active (non-deleted) companies
     */
    public List<Tenant> getActiveCompanies() {
        return tenantRepo.findAllActive();
    }

    /**
     * Get all deleted companies (recycle bin)
     */
    public List<Tenant> getDeletedCompanies() {
        return tenantRepo.findAllDeleted();
    }

    /**
     * Get company statistics
     */
    public Map<String, Object> getCompanyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", tenantRepo.countActive());
        stats.put("deletedCount", tenantRepo.countDeleted());
        stats.put("totalCount", tenantRepo.count());
        return stats;
    }

    // System tenant that cannot be deleted
    private static final String SYSTEM_TENANT_ID = "SASA001";
    
    /**
     * Soft delete a company - moves to recycle bin
     */
    @Transactional
    public Tenant softDeleteCompany(String tenantId, String deletedBy, String reason) {
        // Prevent deleting system tenant
        if (SYSTEM_TENANT_ID.equals(tenantId)) {
            throw new RuntimeException("Cannot delete the system tenant (SASA001). This is the default tenant used for Super Admin access.");
        }
        
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + tenantId));

        if (Boolean.TRUE.equals(tenant.getDeleted())) {
            throw new RuntimeException("Company is already in recycle bin");
        }

        tenant.setDeleted(true);
        tenant.setDeletedAt(LocalDateTime.now());
        tenant.setDeletedBy(deletedBy);
        tenant.setDeleteReason(reason);
        tenant.setIsActive(false);

        log.info("🗑️ Company soft deleted: {} by {}", tenantId, deletedBy);
        return tenantRepo.save(tenant);
    }

    /**
     * Restore a company from recycle bin
     */
    @Transactional
    public Tenant restoreCompany(String tenantId) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + tenantId));

        if (!Boolean.TRUE.equals(tenant.getDeleted())) {
            throw new RuntimeException("Company is not in recycle bin");
        }

        tenant.setDeleted(false);
        tenant.setDeletedAt(null);
        tenant.setDeletedBy(null);
        tenant.setDeleteReason(null);
        tenant.setIsActive(true);

        log.info("♻️ Company restored: {}", tenantId);
        return tenantRepo.save(tenant);
    }

    /**
     * Permanently delete a company and ALL its data
     * This is irreversible!
     */
    @Transactional
    public Map<String, Object> permanentDeleteCompany(String tenantId, String deletedBy) {
        // Prevent deleting system tenant
        if (SYSTEM_TENANT_ID.equals(tenantId)) {
            throw new RuntimeException("Cannot permanently delete the system tenant (SASA001). This is the default tenant used for Super Admin access.");
        }
        
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + tenantId));

        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", tenantId);
        result.put("companyName", tenant.getName());
        result.put("deletedBy", deletedBy);
        result.put("deletedAt", LocalDateTime.now().toString());

        Map<String, Integer> deletedCounts = new LinkedHashMap<>();

        // Delete all related data in order (respecting foreign keys)
        log.warn("🔥 PERMANENT DELETE starting for tenant: {}", tenantId);

        // 1. Attendance data
        deletedCounts.put("attendance_punch", deleteByTenantId("attendance_punch", tenantId));
        deletedCounts.put("attendance_punches", deleteByTenantId("attendance_punches", tenantId));
        deletedCounts.put("attendance_session", deleteByTenantId("attendance_session", tenantId));
        deletedCounts.put("attendance_day", deleteByTenantId("attendance_day", tenantId));
        deletedCounts.put("overtime_allowance", deleteByTenantId("overtime_allowance", tenantId));

        // 2. Import data
        deletedCounts.put("import_error", deleteByTenantId("import_error", tenantId));
        deletedCounts.put("import_batch", deleteByTenantId("import_batch", tenantId));

        // 3. Leave data
        deletedCounts.put("leave_calendar", deleteByTenantId("leave_calendar", tenantId));
        deletedCounts.put("employee_leave", deleteByTenantId("employee_leave", tenantId));
        deletedCounts.put("leave_ledger", deleteByTenantId("leave_ledger", tenantId));
        deletedCounts.put("employee_leave_allocation", deleteByTenantId("employee_leave_allocation", tenantId));
        deletedCounts.put("leave_type", deleteByTenantId("leave_type", tenantId));

        // 4. Loan data
        deletedCounts.put("loan_repayments", deleteByTenantId("loan_repayments", tenantId));
        deletedCounts.put("loans", deleteByTenantId("loans", tenantId));

        // 5. Payroll data
        deletedCounts.put("payroll", deleteByTenantId("payroll", tenantId));
        deletedCounts.put("salary_overtime_config", deleteByTenantId("salary_overtime_config", tenantId));

        // 6. Biometric device mappings (must be before employees and devices)
        deletedCounts.put("biometric_device_mappings", deleteByTenantId("biometric_device_mappings", tenantId));
        
        // 7. Employee assignments
        deletedCounts.put("employee_shift_assignments", deleteByTenantId("employee_shift_assignments", tenantId));

        // 8. Employees
        deletedCounts.put("employees", deleteByTenantId("employees", tenantId));
        
        // 9. Biometric devices (after employees since employees reference devices)
        deletedCounts.put("biometric_devices", deleteByTenantId("biometric_devices", tenantId));

        // 8. Shifts and holidays
        deletedCounts.put("shifts", deleteByTenantId("shifts", tenantId));
        deletedCounts.put("holidays", deleteByTenantId("holidays", tenantId));
        deletedCounts.put("weekly_off_config", deleteByTenantId("weekly_off_config", tenantId));

        // 9. Users (admin accounts) - PRESERVE SUPER_ADMIN users
        deletedCounts.put("refresh_tokens", deleteByTenantId("refresh_tokens", tenantId));
        deletedCounts.put("login_audit", deleteByTenantId("login_audit", tenantId));
        deletedCounts.put("users", deleteNonSuperAdminUsers(tenantId));

        // 10. Registration and trial tracking
        deletedCounts.put("registration_attempts", deleteRegistrationAttempts(tenantId));
        deletedCounts.put("trial_tracking", deleteByTenantId("trial_tracking", tenantId));
        
        // Delete company_registrations by tenant_id AND by email (to catch both activated and pending)
        int regByTenant = deleteByTenantId("company_registrations", tenantId);
        int regByEmail = deleteCompanyRegistrationsByTenantEmail(tenantId);
        deletedCounts.put("company_registrations", regByTenant + regByEmail);

        // 11. Finally delete the tenant
        tenantRepo.deleteById(tenantId);
        deletedCounts.put("tenant", 1);

        result.put("deletedRecords", deletedCounts);
        result.put("totalRecordsDeleted", deletedCounts.values().stream().mapToInt(Integer::intValue).sum());

        log.warn("🔥 PERMANENT DELETE completed for tenant: {}. Total records: {}", 
                tenantId, result.get("totalRecordsDeleted"));

        return result;
    }

    /**
     * Helper to delete records by tenant_id
     */
    private int deleteByTenantId(String tableName, String tenantId) {
        try {
            String sql = "DELETE FROM " + tableName + " WHERE tenant_id = ?";
            int count = jdbcTemplate.update(sql, tenantId);
            if (count > 0) {
                log.info("  Deleted {} records from {}", count, tableName);
            }
            return count;
        } catch (Exception e) {
            // Table might not exist or have different column name
            log.debug("Could not delete from {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    /**
     * Get data count for a company (for display before delete)
     */
    public Map<String, Integer> getCompanyDataCounts(String tenantId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        
        counts.put("employees", countByTenantId("employees", tenantId));
        counts.put("attendance_records", countByTenantId("attendance_day", tenantId));
        counts.put("payroll_records", countByTenantId("payroll", tenantId));
        counts.put("leave_records", countByTenantId("employee_leave", tenantId));
        counts.put("loan_records", countByTenantId("loans", tenantId));
        counts.put("shifts", countByTenantId("shifts", tenantId));
        counts.put("users", countByTenantId("users", tenantId));
        
        return counts;
    }

    private int countByTenantId(String tableName, String tenantId) {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE tenant_id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Delete registration attempts by finding associated email from company_registrations
     */
    private int deleteRegistrationAttempts(String tenantId) {
        try {
            // First find emails associated with this tenant from company_registrations
            String emailSql = "SELECT DISTINCT email FROM company_registrations WHERE tenant_id = ?";
            List<String> emails = jdbcTemplate.queryForList(emailSql, String.class, tenantId);
            
            int totalDeleted = 0;
            for (String email : emails) {
                String deleteSql = "DELETE FROM registration_attempts WHERE email = ?";
                totalDeleted += jdbcTemplate.update(deleteSql, email);
            }
            
            if (totalDeleted > 0) {
                log.info("  Deleted {} registration attempts for tenant {}", totalDeleted, tenantId);
            }
            return totalDeleted;
        } catch (Exception e) {
            log.debug("Could not delete registration_attempts: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Delete company_registrations by email (to catch registrations that might not have tenant_id set yet)
     * Gets email from users table for this tenant
     */
    private int deleteCompanyRegistrationsByTenantEmail(String tenantId) {
        try {
            // Get admin email from users table for this tenant
            String emailSql = "SELECT email FROM users WHERE tenant_id = ? AND role = 'ADMIN' LIMIT 1";
            List<String> emails = jdbcTemplate.queryForList(emailSql, String.class, tenantId);
            
            int totalDeleted = 0;
            for (String email : emails) {
                // Delete by email (catches both activated and pending registrations)
                String deleteSql = "DELETE FROM company_registrations WHERE email = ?";
                int deleted = jdbcTemplate.update(deleteSql, email);
                totalDeleted += deleted;
                if (deleted > 0) {
                    log.info("  Deleted {} company_registrations by email {} for tenant {}", deleted, email, tenantId);
                }
            }
            return totalDeleted;
        } catch (Exception e) {
            log.debug("Could not delete company_registrations by email: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Delete users but preserve SUPER_ADMIN users (they are needed for system access)
     */
    private int deleteNonSuperAdminUsers(String tenantId) {
        try {
            // Delete only non-SUPER_ADMIN users for this tenant
            String sql = "DELETE FROM users WHERE tenant_id = ? AND role != 'SUPER_ADMIN'";
            int count = jdbcTemplate.update(sql, tenantId);
            if (count > 0) {
                log.info("  Deleted {} non-super-admin users from tenant {}", count, tenantId);
            }
            
            // Count remaining super admins (should not be deleted)
            String countSql = "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND role = 'SUPER_ADMIN'";
            Integer superAdminCount = jdbcTemplate.queryForObject(countSql, Integer.class, tenantId);
            if (superAdminCount != null && superAdminCount > 0) {
                log.info("  Preserved {} SUPER_ADMIN users for tenant {}", superAdminCount, tenantId);
            }
            
            return count;
        } catch (Exception e) {
            log.debug("Could not delete users: {}", e.getMessage());
            return 0;
        }
    }
}
