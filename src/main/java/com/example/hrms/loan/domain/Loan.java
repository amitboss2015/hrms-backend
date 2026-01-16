package com.example.hrms.loan.domain;

import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.domain.enums.LoanType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a loan given to an employee.
 * EMI will be deducted from monthly salary.
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

    // Multi-tenancy support (replaces orgId)
    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private String empId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType = LoanType.PERSONAL;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO; // Annual interest rate %

    @Column(nullable = false)
    private Integer tenureMonths;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal emiAmount;

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
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    // Backward compatibility
    public String getOrgId() { return tenantId; }
    public void setOrgId(String orgId) { this.tenantId = orgId; }
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
}
