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
     * Get total monthly EMI/loan deduction for an employee for a specific payroll period
     * Includes:
     * - Regular EMI loans (fixed monthly deductions)
     * - Pending one-time loans (not yet deducted)
     * - Flexible loans (outstanding balance - auto-included for payroll)
     * - Enforced amounts (admin-modified amounts for specific payroll period)
     * Excludes:
     * - One-time loans already deducted in a previous payroll
     */
    public BigDecimal getMonthlyEmiDeduction(String orgId, String empId) {
        return getMonthlyEmiDeduction(orgId, empId, null, null);
    }
    
    /**
     * Get total monthly EMI/loan deduction for an employee for a specific payroll period
     * If month/year provided, checks for enforced amounts
     */
    public BigDecimal getMonthlyEmiDeduction(String orgId, String empId, Integer month, Integer year) {
        // Check for enforced amounts first (admin-modified amounts for this payroll period)
        BigDecimal enforcedAmount = BigDecimal.ZERO;
        if (month != null && year != null) {
            List<Loan> enforcedLoans = loanRepo.findEnforcedLoansForPeriod(orgId, empId, month, year);
            for (Loan loan : enforcedLoans) {
                if (loan.getEnforcedAmount() != null && Boolean.TRUE.equals(loan.getEnforceInPayroll())) {
                    enforcedAmount = enforcedAmount.add(loan.getEnforcedAmount());
                }
            }
        }
        
        // If enforced amounts exist, use them instead of regular calculation
        if (enforcedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return enforcedAmount;
        }
        
        // Regular EMI loans (excluding one-time and flexible)
        BigDecimal regularEmi = loanRepo.sumActiveEmiByEmployee(orgId, empId);
        if (regularEmi == null) regularEmi = BigDecimal.ZERO;
        
        // Pending one-time loans (not yet deducted in any payroll)
        BigDecimal oneTimeAmount = loanRepo.sumPendingOneTimeLoans(orgId, empId);
        if (oneTimeAmount == null) oneTimeAmount = BigDecimal.ZERO;
        
        // Flexible loans - include outstanding balance (auto-deduct full outstanding)
        BigDecimal flexibleOutstanding = getFlexibleLoanOutstanding(orgId, empId);
        if (flexibleOutstanding == null) flexibleOutstanding = BigDecimal.ZERO;
        
        return regularEmi.add(oneTimeAmount).add(flexibleOutstanding);
    }
    
    /**
     * Get pending one-time loans for an employee (not yet deducted)
     */
    public List<Loan> getPendingOneTimeLoans(String tenantId, String empId) {
        return loanRepo.findPendingOneTimeLoans(tenantId, empId);
    }
    
    /**
     * Mark one-time loans as deducted in a payroll
     * NOTE: Flexible loans are NOT processed here - they are handled by updateLoanBalancesForAdjustedAmount()
     * when payroll is approved, so smart adjustment can work correctly (partial deduction)
     */
    @Transactional
    public void markOneTimeLoansAsDeducted(String tenantId, String empId, Long payrollId, Integer month, Integer year) {
        // Mark one-time loans as deducted (these are always fully deducted)
        List<Loan> pendingOneTimeLoans = loanRepo.findPendingOneTimeLoans(tenantId, empId);
        for (Loan loan : pendingOneTimeLoans) {
            loan.markAsDeducted(payrollId, month, year);
            // Also update total paid and outstanding balance
            loan.setTotalPaid(loan.getTotalPaid().add(loan.getEmiAmount()));
            loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(loan.getEmiAmount()));
            loan.setEmisPaid(1);
            // Close the loan since it's fully paid
            if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setStatus(LoanStatus.CLOSED);
                loan.setClosedDate(java.time.LocalDate.now());
            }
            loanRepo.save(loan);
        }
        
        // DO NOT process flexible loans here!
        // Flexible loans are handled by updateLoanBalancesForAdjustedAmount() when payroll is APPROVED
        // This allows smart adjustment to deduct only the adjusted amount (e.g., ₹1,953 instead of ₹5,000)
        // and leave the remaining balance outstanding (e.g., ₹3,047)
    }
    
    /**
     * Clear loan deduction associations when payroll is deleted
     * This makes the loans available for re-deduction in future payrolls
     * Handles both one-time and flexible loans
     */
    @Transactional
    public void clearOneTimeLoanAssociations(Long payrollId) {
        List<Loan> loans = loanRepo.findByDeductedInPayrollId(payrollId);
        for (Loan loan : loans) {
            // Determine the amount to reverse
            BigDecimal amountToReverse;
            if (loan.getDeductedAmount() != null) {
                // Flexible loan - use stored deducted amount
                amountToReverse = loan.getDeductedAmount();
            } else {
                // One-time loan - use EMI amount
                amountToReverse = loan.getEmiAmount();
            }
            
            // Reverse the deduction
            loan.setTotalPaid(loan.getTotalPaid().subtract(amountToReverse));
            loan.setOutstandingBalance(loan.getOutstandingBalance().add(amountToReverse));
            loan.setEmisPaid(0);
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setClosedDate(null);
            loan.clearDeductionAssociation();
            loanRepo.save(loan);
        }
    }
    
    /**
     * Clear all loan associations for a month/year (when bulk deleting payroll)
     * Handles both one-time and flexible loans
     */
    @Transactional
    public void clearOneTimeLoanAssociationsForMonth(String tenantId, Integer month, Integer year) {
        List<Loan> loans = loanRepo.findByDeductedInMonthYear(tenantId, month, year);
        for (Loan loan : loans) {
            // Determine the amount to reverse
            BigDecimal amountToReverse;
            if (loan.getDeductedAmount() != null) {
                // Flexible loan - use stored deducted amount
                amountToReverse = loan.getDeductedAmount();
            } else {
                // One-time loan - use EMI amount
                amountToReverse = loan.getEmiAmount();
            }
            
            // Reverse the deduction
            loan.setTotalPaid(loan.getTotalPaid().subtract(amountToReverse));
            loan.setOutstandingBalance(loan.getOutstandingBalance().add(amountToReverse));
            loan.setEmisPaid(0);
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setClosedDate(null);
            loan.clearDeductionAssociation();
            loanRepo.save(loan);
        }
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
     * Overloaded method for backward compatibility
     */
    public LoanRepayment processEmiPayment(Long loanId, Long payrollId) {
        return processEmiPayment(loanId, payrollId, null);
    }
    
    /**
     * Process EMI payment (called during payroll processing)
     * @param description Optional description for the payment (e.g., "Deducted in salary of DECEMBER 2025")
     */
    public LoanRepayment processEmiPayment(Long loanId, Long payrollId, String description) {
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
        
        // Add description for payroll deduction
        if (description != null && !description.trim().isEmpty()) {
            nextEmi.setRemarks(description);
        } else if (payrollId != null) {
            nextEmi.setRemarks("Deducted in salary through payroll");
        }
        
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
            loan.setOutstandingBalance(BigDecimal.ZERO);
        }

        loanRepo.save(loan);
        return nextEmi;
    }
    
    /**
     * Record payroll deduction for partial loan payment (smart adjustment)
     * Creates a repayment record with description showing amount deducted and remaining balance
     */
    public LoanRepayment recordPayrollDeduction(Long loanId, BigDecimal amount, Long payrollId, 
                                                 Integer month, Integer year, String description) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        
        // Create a repayment record for this partial deduction
        LoanRepayment repayment = new LoanRepayment();
        repayment.setLoanId(loanId);
        
        // Get next EMI number (or use current paid count + 1)
        int nextEmiNumber = (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0) + 1;
        repayment.setEmiNumber(nextEmiNumber);
        repayment.setDueDate(LocalDate.of(year, month, 1));
        repayment.setPaidDate(LocalDate.now());
        repayment.setEmiAmount(amount); // Scheduled amount
        repayment.setAmountPaid(amount); // Actual amount paid (adjusted)
        repayment.setBalanceAfterPayment(loan.getOutstandingBalance().subtract(amount));
        repayment.setRepaymentMode(RepaymentMode.SALARY_DEDUCTION);
        repayment.setPayrollId(payrollId);
        repayment.setIsPaid(true);
        repayment.setRemarks(description); // Description like "Deducted in salary of DECEMBER 2025 (₹2000 deducted, ₹3000 remaining)"
        
        repaymentRepo.save(repayment);
        
        // Update loan balance
        loan.setTotalPaid(loan.getTotalPaid().add(amount));
        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(amount));
        
        // Mark as deducted in this payroll
        loan.markAsDeducted(payrollId, month, year, amount);
        
        // Close loan if fully paid
        if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedDate(LocalDate.now());
            loan.setOutstandingBalance(BigDecimal.ZERO);
        }
        
        loanRepo.save(loan);
        return repayment;
    }

    /**
     * Get all loans for organization
     */
    public List<Loan> getAllLoans(String orgId) {
        return loanRepo.findByOrgIdOrderBySanctionDateDesc(orgId);
    }

    /**
     * Update loan - comprehensive update supporting all fields
     */
    public Loan updateLoan(Long loanId, Loan updates) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        
        // Update basic fields
        if (updates.getRemarks() != null) loan.setRemarks(updates.getRemarks());
        if (updates.getStatus() != null) {
            // Validate status - cannot be CLOSED if outstanding balance > 0
            if (updates.getStatus() == LoanStatus.CLOSED && 
                loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("Cannot close loan with outstanding balance: " + loan.getOutstandingBalance());
            }
            loan.setStatus(updates.getStatus());
            if (updates.getStatus() == LoanStatus.CLOSED && loan.getClosedDate() == null) {
                loan.setClosedDate(java.time.LocalDate.now());
            }
        }
        
        // Update loan amounts (if not already paid)
        if (updates.getPrincipalAmount() != null && loan.getTotalPaid().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal oldPrincipal = loan.getPrincipalAmount();
            BigDecimal newPrincipal = updates.getPrincipalAmount();
            BigDecimal difference = newPrincipal.subtract(oldPrincipal);
            
            loan.setPrincipalAmount(newPrincipal);
            loan.setOutstandingBalance(loan.getOutstandingBalance().add(difference));
            loan.setTotalRepayable(loan.getTotalRepayable().add(difference));
        }
        
        // Update EMI amount (if not already paid)
        if (updates.getEmiAmount() != null && loan.getEmisPaid() == 0) {
            loan.setEmiAmount(updates.getEmiAmount());
        }
        
        // Update tenure (if not already paid)
        if (updates.getTenureMonths() != null && loan.getEmisPaid() == 0) {
            loan.setTenureMonths(updates.getTenureMonths());
        }
        
        // Update interest rate (if not already paid)
        if (updates.getInterestRate() != null && loan.getTotalPaid().compareTo(BigDecimal.ZERO) == 0) {
            loan.setInterestRate(updates.getInterestRate());
            // Recalculate total repayable if principal and tenure exist
            if (loan.getPrincipalAmount() != null && loan.getTenureMonths() != null && loan.getTenureMonths() > 0) {
                BigDecimal newEmi = calculateEmi(loan.getPrincipalAmount(), loan.getInterestRate(), loan.getTenureMonths());
                loan.setEmiAmount(newEmi);
                loan.setTotalRepayable(newEmi.multiply(BigDecimal.valueOf(loan.getTenureMonths())));
                loan.setOutstandingBalance(loan.getTotalRepayable().subtract(loan.getTotalPaid()));
            }
        }
        
        // Prevent loan type changes - loan type can only be set at creation time
        if (updates.getLoanType() != null && !updates.getLoanType().equals(loan.getLoanType())) {
            throw new IllegalStateException("Loan type cannot be changed after creation. Current type: " + loan.getLoanType() + 
                    ", Attempted: " + updates.getLoanType() + ". Loan type can only be set during creation.");
        }
        
        // Prevent isOneTimeDeduction and isFlexibleDeduction changes after creation
        // These determine EMI vs flexible vs one-time, and cannot be changed once set
        if (updates.getIsOneTimeDeduction() != null && 
            !updates.getIsOneTimeDeduction().equals(loan.getIsOneTimeDeduction())) {
            throw new IllegalStateException("Loan deduction type (one-time/flexible) cannot be changed after creation. " +
                    "Current: " + loan.getIsOneTimeDeduction() + ", Attempted: " + updates.getIsOneTimeDeduction());
        }
        if (updates.getIsFlexibleDeduction() != null && 
            !updates.getIsFlexibleDeduction().equals(loan.getIsFlexibleDeduction())) {
            throw new IllegalStateException("Loan deduction type (one-time/flexible) cannot be changed after creation. " +
                    "Current: " + loan.getIsFlexibleDeduction() + ", Attempted: " + updates.getIsFlexibleDeduction());
        }
        
        // Update enforced amount fields
        if (updates.getEnforcedAmount() != null) {
            loan.setEnforcedAmount(updates.getEnforcedAmount());
        }
        if (updates.getEnforcedForMonth() != null) {
            loan.setEnforcedForMonth(updates.getEnforcedForMonth());
        }
        if (updates.getEnforcedForYear() != null) {
            loan.setEnforcedForYear(updates.getEnforcedForYear());
        }
        if (updates.getEnforceInPayroll() != null) {
            loan.setEnforceInPayroll(updates.getEnforceInPayroll());
        }
        
        // Ensure status matches outstanding balance
        if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0 && loan.getStatus() != LoanStatus.CLOSED) {
            loan.setStatus(LoanStatus.CLOSED);
            if (loan.getClosedDate() == null) {
                loan.setClosedDate(java.time.LocalDate.now());
            }
        } else if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0 && loan.getStatus() == LoanStatus.CLOSED) {
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setClosedDate(null);
        }
        
        return loanRepo.save(loan);
    }
    
    /**
     * Delete loan permanently (only if no payments made)
     */
    @Transactional
    public void deleteLoan(Long loanId) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        
        // Cannot delete if payments have been made
        if (loan.getTotalPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot delete loan with payment history. Cancel the loan instead.");
        }
        
        // Delete associated repayments if any
        List<LoanRepayment> repayments = repaymentRepo.findByLoanIdOrderByEmiNumberAsc(loanId);
        if (!repayments.isEmpty()) {
            repaymentRepo.deleteAll(repayments);
        }
        
        // Delete the loan
        loanRepo.delete(loan);
    }

    /**
     * Record partial payment with description (for manual payments)
     */
    public LoanRepayment recordPartialPayment(Long loanId, BigDecimal amount, String description) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        
        // Validate amount
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(loan.getOutstandingBalance()) > 0) {
            throw new IllegalArgumentException("Amount cannot exceed outstanding balance: " + loan.getOutstandingBalance());
        }
        
        // Create repayment record
        LoanRepayment repayment = new LoanRepayment();
        repayment.setLoanId(loanId);
        int nextEmiNumber = (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0) + 1;
        repayment.setEmiNumber(nextEmiNumber);
        repayment.setDueDate(LocalDate.now());
        repayment.setPaidDate(LocalDate.now());
        repayment.setEmiAmount(amount);
        repayment.setAmountPaid(amount);
        repayment.setBalanceAfterPayment(loan.getOutstandingBalance().subtract(amount));
        repayment.setRepaymentMode(RepaymentMode.MANUAL_PAYMENT);
        repayment.setIsPaid(true);
        repayment.setRemarks(description != null && !description.trim().isEmpty() 
                ? description 
                : "Manual payment recorded");
        
        repaymentRepo.save(repayment);
        
        // Update loan
        loan.setTotalPaid(loan.getTotalPaid().add(amount));
        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(amount));
        
        // Close loan if fully paid
        if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedDate(LocalDate.now());
            loan.setOutstandingBalance(BigDecimal.ZERO);
        }
        
        loanRepo.save(loan);
        return repayment;
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
