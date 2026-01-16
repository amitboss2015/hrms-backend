package com.example.hrms.payroll.repo;

import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.domain.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    
    // ============ TENANT-AWARE METHODS ============
    
    Optional<Payroll> findByTenantIdAndEmpIdAndYearAndMonth(
        String tenantId, String empId, Integer year, Integer month);
    
    List<Payroll> findByTenantIdAndYearAndMonthOrderByEmpIdAsc(
        String tenantId, Integer year, Integer month);
    
    List<Payroll> findByTenantIdAndYearAndMonthAndStatus(
        String tenantId, Integer year, Integer month, PayrollStatus status);
    
    List<Payroll> findByTenantIdAndEmpIdOrderByYearDescMonthDesc(String tenantId, String empId);
    
    List<Payroll> findByTenantIdAndYearOrderByMonthDescEmpIdAsc(String tenantId, Integer year);

    // Summary queries
    @Query("SELECT SUM(p.grossSalary) FROM Payroll p WHERE p.tenantId = :tenantId AND p.year = :year AND p.month = :month")
    BigDecimal sumGrossSalary(@Param("tenantId") String tenantId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.tenantId = :tenantId AND p.year = :year AND p.month = :month")
    BigDecimal sumNetSalary(@Param("tenantId") String tenantId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.pfEmployee) FROM Payroll p WHERE p.tenantId = :tenantId AND p.year = :year AND p.month = :month")
    BigDecimal sumPfEmployee(@Param("tenantId") String tenantId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.pfCompany) FROM Payroll p WHERE p.tenantId = :tenantId AND p.year = :year AND p.month = :month")
    BigDecimal sumPfCompany(@Param("tenantId") String tenantId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.esiEmployee) FROM Payroll p WHERE p.tenantId = :tenantId AND p.year = :year AND p.month = :month")
    BigDecimal sumEsi(@Param("tenantId") String tenantId, @Param("year") int year, @Param("month") int month);

    // Count by status
    long countByTenantIdAndYearAndMonthAndStatus(String tenantId, int year, int month, PayrollStatus status);

    // Find by payment mode
    List<Payroll> findByTenantIdAndYearAndMonthAndPaymentModeOrderByEmpIdAsc(
        String tenantId, int year, int month, com.example.hrms.payroll.domain.enums.PaymentMode paymentMode);
    
    // ============ LEGACY METHODS (backward compatibility) ============
    
    @Deprecated
    default Optional<Payroll> findByOrgIdAndEmpIdAndYearAndMonth(String orgId, String empId, Integer year, Integer month) {
        return findByTenantIdAndEmpIdAndYearAndMonth(orgId, empId, year, month);
    }
    
    @Deprecated
    default List<Payroll> findByOrgIdAndYearAndMonthOrderByEmpIdAsc(String orgId, Integer year, Integer month) {
        return findByTenantIdAndYearAndMonthOrderByEmpIdAsc(orgId, year, month);
    }
    
    @Deprecated
    default List<Payroll> findByOrgIdAndYearAndMonthAndStatus(String orgId, Integer year, Integer month, PayrollStatus status) {
        return findByTenantIdAndYearAndMonthAndStatus(orgId, year, month, status);
    }
    
    @Deprecated
    default List<Payroll> findByOrgIdAndEmpIdOrderByYearDescMonthDesc(String orgId, String empId) {
        return findByTenantIdAndEmpIdOrderByYearDescMonthDesc(orgId, empId);
    }
    
    @Deprecated
    default List<Payroll> findByOrgIdAndYearOrderByMonthDescEmpIdAsc(String orgId, Integer year) {
        return findByTenantIdAndYearOrderByMonthDescEmpIdAsc(orgId, year);
    }
}
