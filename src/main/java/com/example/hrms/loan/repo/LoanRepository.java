package com.example.hrms.loan.repo;

import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    
    List<Loan> findByOrgIdAndEmpIdOrderBySanctionDateDesc(String orgId, String empId);
    
    List<Loan> findByOrgIdAndEmpIdAndStatus(String orgId, String empId, LoanStatus status);
    
    List<Loan> findByOrgIdAndStatusOrderBySanctionDateDesc(String orgId, LoanStatus status);
    
    List<Loan> findByOrgIdOrderBySanctionDateDesc(String orgId);
    
    @Query("SELECT COALESCE(SUM(l.emiAmount), 0) FROM Loan l " +
           "WHERE l.orgId = :orgId AND l.empId = :empId AND l.status = 'ACTIVE'")
    BigDecimal sumActiveEmiByEmployee(@Param("orgId") String orgId, @Param("empId") String empId);
    
    @Query("SELECT COALESCE(SUM(l.outstandingBalance), 0) FROM Loan l " +
           "WHERE l.orgId = :orgId AND l.empId = :empId AND l.status = 'ACTIVE'")
    BigDecimal sumOutstandingByEmployee(@Param("orgId") String orgId, @Param("empId") String empId);
}
