package com.example.hrms.loan.domain;

import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.domain.enums.LoanType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a loan given to an employee.
 * EMI will be deducted from monthly salary.
 * Supports: Regular EMI loans, Salary Advances, One-time adjustments
 */
@Entity
@Table(name = "loans", 
       indexes = {
           @Index(name = "idx_loan_emp", columnList = "empId"),
           @Index(name = "idx_loan_tenant", columnList = "tenantId")
       })
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;
    
    // Legacy column - keep for backward compatibility, maps to same value as tenantId
    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(nullable = false)
    private String empId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType = LoanType.PERSONAL;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO; // Annual interest rate %

    // Tenure is optional for one-time adjustments (e.g., salary advance paid in full)
    @Column
    private Integer tenureMonths;

    // EMI is optional for one-time adjustments
    @Column(precision = 12, scale = 2)
    private BigDecimal emiAmount;
    
    // If true, deduct full amount from next payroll instead of EMI schedule
    @Column(nullable = false)
    private Boolean isOneTimeDeduction = false;
    
    // If true, admin can adjust deduction amount each month (no fixed EMI)
    // Outstanding balance is tracked, admin decides how much to deduct
    @Column(nullable = false)
    private Boolean isFlexibleDeduction = false;
    
    // ========== PAYROLL ASSOCIATION FOR ONE-TIME LOANS ==========
    // Tracks which payroll this one-time loan was deducted in
    // When payroll is deleted, this should be cleared to allow re-deduction
    @Column(name = "deducted_in_payroll_id")
    private Long deductedInPayrollId;
    
    @Column(name = "deducted_in_month")
    private Integer deductedInMonth;
    
    @Column(name = "deducted_in_year")
    private Integer deductedInYear;
    
    // Store the amount deducted (for flexible loans where we deduct outstanding balance)
    @Column(name = "deducted_amount", precision = 12, scale = 2)
    private BigDecimal deductedAmount;
    
    // Admin can enforce/modify loan deduction amount for specific payroll period
    // If set, this amount will be used instead of EMI amount for that payroll
    @Column(name = "enforced_amount", precision = 12, scale = 2)
    private BigDecimal enforcedAmount;
    
    @Column(name = "enforced_for_month")
    private Integer enforcedForMonth;
    
    @Column(name = "enforced_for_year")
    private Integer enforcedForYear;
    
    @Column(name = "enforce_in_payroll", nullable = false)
    private Boolean enforceInPayroll = false; // If true, admin wants to enforce this loan in payroll

    @Column(nullable = false)
    private LocalDate sanctionDate;

    private LocalDate firstEmiDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRepayable; // Principal + Interest

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingBalance;

    private Integer emisPaid = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status = LoanStatus.ACTIVE;

    private String remarks;

    private LocalDate closedDate;

    public Loan() {}

    // Calculate EMI using flat rate method (simple)
    public static BigDecimal calculateFlatEmi(BigDecimal principal, BigDecimal annualRate, int months) {
        BigDecimal totalInterest = principal.multiply(annualRate).divide(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal total = principal.add(totalInterest);
        return total.divide(BigDecimal.valueOf(months), 0, java.math.RoundingMode.CEILING);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { 
        this.tenantId = tenantId; 
        this.orgId = tenantId; // Keep both in sync
    }
    
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { 
        this.orgId = orgId; 
        this.tenantId = orgId; // Keep both in sync
    }
    
    public Boolean getIsOneTimeDeduction() { return isOneTimeDeduction; }
    public void setIsOneTimeDeduction(Boolean isOneTimeDeduction) { this.isOneTimeDeduction = isOneTimeDeduction; }
    
    public Boolean getIsFlexibleDeduction() { return isFlexibleDeduction; }
    public void setIsFlexibleDeduction(Boolean isFlexibleDeduction) { this.isFlexibleDeduction = isFlexibleDeduction; }
    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }
    public LoanType getLoanType() { return loanType; }
    public void setLoanType(LoanType loanType) { this.loanType = loanType; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public LocalDate getSanctionDate() { return sanctionDate; }
    public void setSanctionDate(LocalDate sanctionDate) { this.sanctionDate = sanctionDate; }
    public LocalDate getFirstEmiDate() { return firstEmiDate; }
    public void setFirstEmiDate(LocalDate firstEmiDate) { this.firstEmiDate = firstEmiDate; }
    public BigDecimal getTotalRepayable() { return totalRepayable; }
    public void setTotalRepayable(BigDecimal totalRepayable) { this.totalRepayable = totalRepayable; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }
    public Integer getEmisPaid() { return emisPaid; }
    public void setEmisPaid(Integer emisPaid) { this.emisPaid = emisPaid; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDate getClosedDate() { return closedDate; }
    public void setClosedDate(LocalDate closedDate) { this.closedDate = closedDate; }
    
    // Payroll association getters/setters
    public Long getDeductedInPayrollId() { return deductedInPayrollId; }
    public void setDeductedInPayrollId(Long deductedInPayrollId) { this.deductedInPayrollId = deductedInPayrollId; }
    
    public Integer getDeductedInMonth() { return deductedInMonth; }
    public void setDeductedInMonth(Integer deductedInMonth) { this.deductedInMonth = deductedInMonth; }
    
    public Integer getDeductedInYear() { return deductedInYear; }
    public void setDeductedInYear(Integer deductedInYear) { this.deductedInYear = deductedInYear; }
    
    public BigDecimal getDeductedAmount() { return deductedAmount; }
    public void setDeductedAmount(BigDecimal deductedAmount) { this.deductedAmount = deductedAmount; }
    
    // Enforced amount getters/setters
    public BigDecimal getEnforcedAmount() { return enforcedAmount; }
    public void setEnforcedAmount(BigDecimal enforcedAmount) { this.enforcedAmount = enforcedAmount; }
    
    public Integer getEnforcedForMonth() { return enforcedForMonth; }
    public void setEnforcedForMonth(Integer enforcedForMonth) { this.enforcedForMonth = enforcedForMonth; }
    
    public Integer getEnforcedForYear() { return enforcedForYear; }
    public void setEnforcedForYear(Integer enforcedForYear) { this.enforcedForYear = enforcedForYear; }
    
    public Boolean getEnforceInPayroll() { return enforceInPayroll; }
    public void setEnforceInPayroll(Boolean enforceInPayroll) { this.enforceInPayroll = enforceInPayroll; }
    
    /**
     * Check if this one-time/flexible loan has already been deducted in a payroll
     */
    public boolean isAlreadyDeducted() {
        return deductedInPayrollId != null;
    }
    
    /**
     * Mark this loan as deducted in the given payroll
     */
    public void markAsDeducted(Long payrollId, Integer month, Integer year) {
        this.deductedInPayrollId = payrollId;
        this.deductedInMonth = month;
        this.deductedInYear = year;
    }
    
    /**
     * Mark as deducted with amount (for flexible loans)
     */
    public void markAsDeducted(Long payrollId, Integer month, Integer year, BigDecimal amount) {
        this.deductedInPayrollId = payrollId;
        this.deductedInMonth = month;
        this.deductedInYear = year;
        this.deductedAmount = amount;
    }
    
    /**
     * Clear deduction association (when payroll is deleted)
     */
    public void clearDeductionAssociation() {
        this.deductedInPayrollId = null;
        this.deductedInMonth = null;
        this.deductedInYear = null;
        this.deductedAmount = null;
    }
}
