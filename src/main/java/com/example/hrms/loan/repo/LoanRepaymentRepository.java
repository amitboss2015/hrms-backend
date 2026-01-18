package com.example.hrms.loan.repo;

import com.example.hrms.loan.domain.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    
    List<LoanRepayment> findByLoanIdOrderByEmiNumberAsc(Long loanId);
    
    List<LoanRepayment> findByLoanIdAndIsPaidFalseOrderByEmiNumberAsc(Long loanId);
    
    Optional<LoanRepayment> findTopByLoanIdAndIsPaidFalseOrderByEmiNumberAsc(Long loanId);
    
    List<LoanRepayment> findByPayrollId(Long payrollId);
    
    // Find upcoming dues within a date range
    List<LoanRepayment> findByIsPaidFalseAndDueDateBetweenOrderByDueDateAsc(LocalDate startDate, LocalDate endDate);
    
    // Find overdue EMIs
    List<LoanRepayment> findByIsPaidFalseAndDueDateBeforeOrderByDueDateAsc(LocalDate date);
    
    // Find repayments paid within a date range
    List<LoanRepayment> findByPaidDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find repayments due within a date range
    List<LoanRepayment> findByDueDateBetween(LocalDate startDate, LocalDate endDate);
}
