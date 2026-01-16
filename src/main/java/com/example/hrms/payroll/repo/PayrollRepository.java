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
    
    Optional<Payroll> findByOrgIdAndEmpIdAndYearAndMonth(
        String orgId, String empId, Integer year, Integer month);
    
    List<Payroll> findByOrgIdAndYearAndMonthOrderByEmpIdAsc(
        String orgId, Integer year, Integer month);
    
    List<Payroll> findByOrgIdAndYearAndMonthAndStatus(
        String orgId, Integer year, Integer month, PayrollStatus status);
    
    List<Payroll> findByOrgIdAndEmpIdOrderByYearDescMonthDesc(String orgId, String empId);
    
    List<Payroll> findByOrgIdAndYearOrderByMonthDescEmpIdAsc(String orgId, Integer year);

    // Summary queries
    @Query("SELECT SUM(p.grossSalary) FROM Payroll p WHERE p.orgId = :orgId AND p.year = :year AND p.month = :month")
    BigDecimal sumGrossSalary(@Param("orgId") String orgId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.orgId = :orgId AND p.year = :year AND p.month = :month")
    BigDecimal sumNetSalary(@Param("orgId") String orgId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.pfEmployee) FROM Payroll p WHERE p.orgId = :orgId AND p.year = :year AND p.month = :month")
    BigDecimal sumPfEmployee(@Param("orgId") String orgId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.pfCompany) FROM Payroll p WHERE p.orgId = :orgId AND p.year = :year AND p.month = :month")
    BigDecimal sumPfCompany(@Param("orgId") String orgId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.esiEmployee) FROM Payroll p WHERE p.orgId = :orgId AND p.year = :year AND p.month = :month")
    BigDecimal sumEsi(@Param("orgId") String orgId, @Param("year") int year, @Param("month") int month);

    // Count by status
    long countByOrgIdAndYearAndMonthAndStatus(String orgId, int year, int month, PayrollStatus status);

    // Find by payment mode
    List<Payroll> findByOrgIdAndYearAndMonthAndPaymentModeOrderByEmpIdAsc(
        String orgId, int year, int month, com.example.hrms.payroll.domain.enums.PaymentMode paymentMode);
}
