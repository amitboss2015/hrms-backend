package com.example.hrms.loan.service;

import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.LoanRepayment;
import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.domain.enums.RepaymentMode;
import com.example.hrms.loan.repo.LoanRepository;
import com.example.hrms.loan.repo.LoanRepaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LoanService {

    private final LoanRepository loanRepo;
    private final LoanRepaymentRepository repaymentRepo;

    public LoanService(LoanRepository loanRepo, LoanRepaymentRepository repaymentRepo) {
        this.loanRepo = loanRepo;
        this.repaymentRepo = repaymentRepo;
    }

    /**
     * Create a new loan with EMI schedule
     * Supports: 
     * - Regular EMI loans (fixed monthly deductions)
     * - One-time deductions (salary advances)
     * - Flexible deductions (admin adjusts each payroll)
     */
    public Loan createLoan(Loan loan) {
        boolean isOneTime = Boolean.TRUE.equals(loan.getIsOneTimeDeduction());
        boolean isFlexible = Boolean.TRUE.equals(loan.getIsFlexibleDeduction());
        
        // Ensure tenure is set
        if (loan.getTenureMonths() == null || loan.getTenureMonths() < 0) {
            if (isOneTime) {
                loan.setTenureMonths(1);
            } else if (isFlexible) {
                loan.setTenureMonths(0); // No fixed tenure for flexible
            } else {
                loan.setTenureMonths(1);
            }
        }
        
        // Calculate EMI if not provided
        if (loan.getEmiAmount() == null || loan.getEmiAmount().compareTo(BigDecimal.ZERO) == 0) {
            if (isOneTime) {
                loan.setEmiAmount(loan.getPrincipalAmount()); // Full amount
            } else if (isFlexible) {
                loan.setEmiAmount(BigDecimal.ZERO); // No fixed EMI for flexible
            } else {
                loan.setEmiAmount(calculateEmi(loan.getPrincipalAmount(), 
                        loan.getInterestRate(), loan.getTenureMonths()));
            }
        }

        // Calculate total repayable
        BigDecimal totalRepayable;
        if (isFlexible) {
            // For flexible loans, total repayable = principal (no interest typically)
            totalRepayable = loan.getPrincipalAmount();
        } else {
            totalRepayable = loan.getEmiAmount().multiply(BigDecimal.valueOf(loan.getTenureMonths()));
        }
        loan.setTotalRepayable(totalRepayable);
        loan.setOutstandingBalance(totalRepayable);
        loan.setTotalPaid(BigDecimal.ZERO);
        loan.setEmisPaid(0);

        if (loan.getFirstEmiDate() == null && !isFlexible) {
            // For regular and one-time, deduct from next payroll (1st of next month)
            loan.setFirstEmiDate(loan.getSanctionDate().plusMonths(1).withDayOfMonth(1));
        }

        Loan saved = loanRepo.save(loan);

        // Create EMI schedule only for non-flexible loans
        if (!isFlexible) {
            createEmiSchedule(saved);
        }

        return saved;
    }

    /**
     * Create EMI schedule for a loan
     */
    private void createEmiSchedule(Loan loan) {
        BigDecimal balance = loan.getTotalRepayable();
        BigDecimal principalPerEmi = loan.getPrincipalAmount()
                .divide(BigDecimal.valueOf(loan.getTenureMonths()), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerEmi = loan.getEmiAmount().subtract(principalPerEmi);

        List<LoanRepayment> schedule = new ArrayList<>();
        LocalDate dueDate = loan.getFirstEmiDate();

        for (int i = 1; i <= loan.getTenureMonths(); i++) {
            LoanRepayment rep = new LoanRepayment();
            rep.setLoanId(loan.getId());
            rep.setEmiNumber(i);
            rep.setDueDate(dueDate);
            rep.setEmiAmount(loan.getEmiAmount());
            rep.setPrincipalComponent(principalPerEmi);
            rep.setInterestComponent(interestPerEmi);
            rep.setAmountPaid(BigDecimal.ZERO);
            rep.setIsPaid(false);
            
            balance = balance.subtract(loan.getEmiAmount());
            rep.setBalanceAfterPayment(balance.max(BigDecimal.ZERO));

            schedule.add(rep);
            dueDate = dueDate.plusMonths(1);
        }

        repaymentRepo.saveAll(schedule);
    }

    /**
     * Calculate EMI using flat rate method
     */
    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int months) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) {
            // No interest - simple division
            return principal.divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING);
        }
        // Flat rate method
        BigDecimal totalInterest = principal.multiply(annualRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal total = principal.add(totalInterest);
        return total.divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING);
    }

    /**
     * Get all loans for an employee
     */
    public List<Loan> getEmployeeLoans(String orgId, String empId) {
        return loanRepo.findByOrgIdAndEmpIdOrderBySanctionDateDesc(orgId, empId);
    }

    /**
     * Get active loans for an employee
     */
    public List<Loan> getActiveLoans(String orgId, String empId) {
        return loanRepo.findByOrgIdAndEmpIdAndStatus(orgId, empId, LoanStatus.ACTIVE);
    }

    /**
     * Get total monthly EMI deduction for an employee
     * Only includes fixed EMI loans, not flexible loans
     */
    public BigDecimal getMonthlyEmiDeduction(String orgId, String empId) {
        BigDecimal total = loanRepo.sumActiveEmiByEmployee(orgId, empId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get active flexible loans for an employee
     * Admin can adjust deduction amount for these during payroll
     */
    public List<Loan> getActiveFlexibleLoans(String tenantId, String empId) {
        return loanRepo.findActiveFlexibleLoans(tenantId, empId);
    }
    
    /**
     * Get total outstanding balance for flexible loans
     */
    public BigDecimal getFlexibleLoanOutstanding(String tenantId, String empId) {
        List<Loan> flexibleLoans = getActiveFlexibleLoans(tenantId, empId);
        return flexibleLoans.stream()
                .map(Loan::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get loan by ID
     */
    public Optional<Loan> getLoan(Long loanId) {
        return loanRepo.findById(loanId);
    }

    /**
     * Get EMI schedule for a loan
     */
    public List<LoanRepayment> getEmiSchedule(Long loanId) {
        return repaymentRepo.findByLoanIdOrderByEmiNumberAsc(loanId);
    }

    /**
     * Process EMI payment (called during payroll processing)
     */
    public LoanRepayment processEmiPayment(Long loanId, Long payrollId) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        LoanRepayment nextEmi = repaymentRepo.findTopByLoanIdAndIsPaidFalseOrderByEmiNumberAsc(loanId)
                .orElse(null);

        if (nextEmi == null) {
            return null; // All EMIs paid
        }

        nextEmi.setIsPaid(true);
        nextEmi.setPaidDate(LocalDate.now());
        nextEmi.setAmountPaid(nextEmi.getEmiAmount());
        nextEmi.setRepaymentMode(RepaymentMode.SALARY_DEDUCTION);
        nextEmi.setPayrollId(payrollId);
        repaymentRepo.save(nextEmi);

        // Update loan
        loan.setTotalPaid(loan.getTotalPaid().add(nextEmi.getEmiAmount()));
        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(nextEmi.getEmiAmount()));
        loan.setEmisPaid(loan.getEmisPaid() + 1);

        // Check if loan is fully paid
        if (loan.getEmisPaid() >= loan.getTenureMonths() || 
            loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedDate(LocalDate.now());
        }

        loanRepo.save(loan);
        return nextEmi;
    }

    /**
     * Get all loans for organization
     */
    public List<Loan> getAllLoans(String orgId) {
        return loanRepo.findByOrgIdOrderBySanctionDateDesc(orgId);
    }

    /**
     * Update loan
     */
    public Loan updateLoan(Long loanId, Loan updates) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        if (updates.getRemarks() != null) loan.setRemarks(updates.getRemarks());
        if (updates.getStatus() != null) loan.setStatus(updates.getStatus());
        return loanRepo.save(loan);
    }

    /**
     * Cancel loan
     */
    public void cancelLoan(Long loanId) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        loan.setStatus(LoanStatus.CANCELLED);
        loanRepo.save(loan);
    }
}
