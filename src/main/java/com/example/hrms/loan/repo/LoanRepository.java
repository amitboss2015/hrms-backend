package com.example.hrms.loan.repo;

import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    
    // ============ TENANT-AWARE METHODS ============
    
    List<Loan> findByTenantIdAndEmpIdOrderBySanctionDateDesc(String tenantId, String empId);
    
    List<Loan> findByTenantIdAndEmpIdAndStatus(String tenantId, String empId, LoanStatus status);
    
    List<Loan> findByTenantIdAndStatusOrderBySanctionDateDesc(String tenantId, LoanStatus status);
    
    List<Loan> findByTenantIdOrderBySanctionDateDesc(String tenantId);
    
    // Sum EMI for active REGULAR loans only (excludes one-time and flexible loans)
    // One-time loans are handled separately by sumPendingOneTimeLoans
    // Flexible loans are handled separately by getFlexibleLoanOutstanding
    @Query("SELECT COALESCE(SUM(l.emiAmount), 0) FROM Loan l " +
           "WHERE l.tenantId = :tenantId AND l.empId = :empId AND l.status = 'ACTIVE' " +
           "AND (l.isFlexibleDeduction = false OR l.isFlexibleDeduction IS NULL) " +
           "AND (l.isOneTimeDeduction = false OR l.isOneTimeDeduction IS NULL)")
    BigDecimal sumActiveEmiByEmployee(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    // Sum outstanding for all active loans including flexible
    @Query("SELECT COALESCE(SUM(l.outstandingBalance), 0) FROM Loan l " +
           "WHERE l.tenantId = :tenantId AND l.empId = :empId AND l.status = 'ACTIVE'")
    BigDecimal sumOutstandingByEmployee(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    // Get all active flexible loans for an employee (for payroll adjustment)
    @Query("SELECT l FROM Loan l WHERE l.tenantId = :tenantId AND l.empId = :empId " +
           "AND l.status = 'ACTIVE' AND l.isFlexibleDeduction = true " +
           "ORDER BY l.sanctionDate DESC")
    List<Loan> findActiveFlexibleLoans(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    // Get active one-time loans that haven't been deducted yet
    @Query("SELECT l FROM Loan l WHERE l.tenantId = :tenantId AND l.empId = :empId " +
           "AND l.status = 'ACTIVE' AND l.isOneTimeDeduction = true " +
           "AND l.deductedInPayrollId IS NULL " +
           "ORDER BY l.sanctionDate ASC")
    List<Loan> findPendingOneTimeLoans(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    // Find all one-time loans associated with a specific payroll
    @Query("SELECT l FROM Loan l WHERE l.deductedInPayrollId = :payrollId")
    List<Loan> findByDeductedInPayrollId(@Param("payrollId") Long payrollId);
    
    // Find all one-time loans associated with a month/year payroll (for bulk delete)
    @Query("SELECT l FROM Loan l WHERE l.tenantId = :tenantId " +
           "AND l.deductedInMonth = :month AND l.deductedInYear = :year")
    List<Loan> findByDeductedInMonthYear(@Param("tenantId") String tenantId, 
                                          @Param("month") Integer month, 
                                          @Param("year") Integer year);
    
    // Sum EMI for pending one-time loans only (not yet deducted)
    @Query("SELECT COALESCE(SUM(l.emiAmount), 0) FROM Loan l " +
           "WHERE l.tenantId = :tenantId AND l.empId = :empId AND l.status = 'ACTIVE' " +
           "AND l.isOneTimeDeduction = true AND l.deductedInPayrollId IS NULL")
    BigDecimal sumPendingOneTimeLoans(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    // ============ LEGACY METHODS (backward compatibility) ============
    
    @Deprecated
    default List<Loan> findByOrgIdAndEmpIdOrderBySanctionDateDesc(String orgId, String empId) {
        return findByTenantIdAndEmpIdOrderBySanctionDateDesc(orgId, empId);
    }
    
    @Deprecated
    default List<Loan> findByOrgIdAndEmpIdAndStatus(String orgId, String empId, LoanStatus status) {
        return findByTenantIdAndEmpIdAndStatus(orgId, empId, status);
    }
    
    @Deprecated
    default List<Loan> findByOrgIdAndStatusOrderBySanctionDateDesc(String orgId, LoanStatus status) {
        return findByTenantIdAndStatusOrderBySanctionDateDesc(orgId, status);
    }
    
    @Deprecated
    default List<Loan> findByOrgIdOrderBySanctionDateDesc(String orgId) {
        return findByTenantIdOrderBySanctionDateDesc(orgId);
    }
}
