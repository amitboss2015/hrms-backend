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
    
    @Query("SELECT COALESCE(SUM(l.emiAmount), 0) FROM Loan l " +
           "WHERE l.tenantId = :tenantId AND l.empId = :empId AND l.status = 'ACTIVE'")
    BigDecimal sumActiveEmiByEmployee(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
    @Query("SELECT COALESCE(SUM(l.outstandingBalance), 0) FROM Loan l " +
           "WHERE l.tenantId = :tenantId AND l.empId = :empId AND l.status = 'ACTIVE'")
    BigDecimal sumOutstandingByEmployee(@Param("tenantId") String tenantId, @Param("empId") String empId);
    
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
