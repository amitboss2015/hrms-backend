package com.example.hrms.loan.domain;

import com.example.hrms.loan.domain.enums.RepaymentMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks individual loan repayments/EMI payments.
 */
@Entity
@Table(name = "loan_repayments",
       indexes = @Index(name = "idx_repayment_loan", columnList = "loanId"))
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long loanId;

    @Column(nullable = false)
    private Integer emiNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate paidDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal emiAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal principalComponent = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal interestComponent = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal balanceAfterPayment;

    @Enumerated(EnumType.STRING)
    private RepaymentMode repaymentMode = RepaymentMode.SALARY_DEDUCTION;

    // Reference to payroll if deducted from salary
    private Long payrollId;

    // Payment reference for manual payments
    private String paymentReference;

    private Boolean isPaid = false;

    private String remarks;

    public LoanRepayment() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public Integer getEmiNumber() { return emiNumber; }
    public void setEmiNumber(Integer emiNumber) { this.emiNumber = emiNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public BigDecimal getPrincipalComponent() { return principalComponent; }
    public void setPrincipalComponent(BigDecimal principalComponent) { this.principalComponent = principalComponent; }
    public BigDecimal getInterestComponent() { return interestComponent; }
    public void setInterestComponent(BigDecimal interestComponent) { this.interestComponent = interestComponent; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
    public BigDecimal getBalanceAfterPayment() { return balanceAfterPayment; }
    public void setBalanceAfterPayment(BigDecimal balanceAfterPayment) { this.balanceAfterPayment = balanceAfterPayment; }
    public RepaymentMode getRepaymentMode() { return repaymentMode; }
    public void setRepaymentMode(RepaymentMode repaymentMode) { this.repaymentMode = repaymentMode; }
    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
