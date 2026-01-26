package com.example.hrms.payroll.domain;

import com.example.hrms.payroll.domain.enums.PayrollStatus;
import com.example.hrms.payroll.domain.enums.PaymentMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Monthly payroll record for an employee.
 * Matches the payment sheet format:
 * SR.NO, EMP.NAME, EMP.ID, BASIC SALARY, INCREMENT, FINAL PAYMENT,
 * WORKING DAY, PRESENT DAY, WORKING DAY AMOUNT, OVERTIME DAY, OVERTIME IN HRS,
 * OT DAY AMOUNT, OT HR AMOUNT, HOUSE RENT, MEDICAL EXP, GROSS SALARY,
 * ESI 0.75%, PF OWN 6%, PF COMPANY 6%, ADV, DUE, NET SALARY, REMARKS
 */
@Entity
@Table(name = "payroll",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "empId", "year", "month"}),
       indexes = {@Index(name = "idx_payroll_emp", columnList = "empId"),
                  @Index(name = "idx_payroll_period", columnList = "year, month"),
                  @Index(name = "idx_payroll_tenant", columnList = "tenantId")})
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;
    
    // Backward compatibility with existing org_id column
    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(nullable = false)
    private String empId;

    private String empName; // Stored for reporting

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    // ============ SALARY STRUCTURE ============
    @Column(precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;      // BASIC SALARY

    @Column(precision = 12, scale = 2)
    private BigDecimal increment = BigDecimal.ZERO;        // INCREMENT

    @Column(precision = 12, scale = 2)
    private BigDecimal finalPayment = BigDecimal.ZERO;     // FINAL PAYMENT (Basic + Increment)

    // ============ ATTENDANCE SUMMARY ============
    private Integer totalWorkingDays;      // WORKING DAY (total working days in month)
    private Integer presentDays;           // PRESENT DAY
    private Integer absentDays;
    private Integer paidLeaveDays;
    private Integer unpaidLeaveDays;
    private Integer halfDays;
    private Integer weeklyOffDays;
    private Integer holidayDays;
    private Integer lateDays;
    private Integer lateDeductionDays;     // 3 lates = 1 absent
    
    // ============ LATE HOURS TRACKING ============
    @Column(precision = 8, scale = 2)
    private BigDecimal totalLateHours = BigDecimal.ZERO;       // Total late hours accumulated
    
    @Column(precision = 12, scale = 2)
    private BigDecimal lateHourCharges = BigDecimal.ZERO;      // Late hour deduction (hourly rate × late hours)
    
    // ============ EARLY CHECKOUT TRACKING ============
    private Integer earlyOutDays = 0;                          // Days with early checkout
    @Column(precision = 8, scale = 2)
    private BigDecimal totalEarlyHours = BigDecimal.ZERO;      // Total early checkout hours
    @Column(precision = 12, scale = 2)
    private BigDecimal earlyHourCharges = BigDecimal.ZERO;     // Early checkout deduction (hourly rate × early hours)

    // ============ WORKING DAY CALCULATION ============
    @Column(precision = 12, scale = 2)
    private BigDecimal workingDayAmount = BigDecimal.ZERO; // WORKING DAY AMOUNT (prorated salary)

    // ============ OVERTIME ============
    private Integer overtimeDays = 0;                      // OVERTIME DAY
    
    @Column(precision = 8, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;    // OVERTIME IN HRS DAY
    
    @Column(precision = 12, scale = 2)
    private BigDecimal overtimeDayAmount = BigDecimal.ZERO; // OVER TIME DAY AMOUNT (OT days * per day rate)
    
    @Column(precision = 12, scale = 2)
    private BigDecimal overtimeHourAmount = BigDecimal.ZERO; // OVER TIME HR AMOUNT (OT hours * hourly rate)
    
    // ============ PAID LEAVE CHARGES ============
    @Column(precision = 12, scale = 2)
    private BigDecimal paidLeaveCharges = BigDecimal.ZERO;  // PAID LEAVE CHARGES (per day rate * paid leave days)

    // ============ ALLOWANCES ============
    @Column(precision = 12, scale = 2)
    private BigDecimal houseRent = BigDecimal.ZERO;        // HOUSE RENT (HRA)

    @Column(precision = 12, scale = 2)
    private BigDecimal medicalExpense = BigDecimal.ZERO;   // MEDICAL EXP.

    @Column(precision = 12, scale = 2)
    private BigDecimal conveyanceAllowance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal specialAllowance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherAllowance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal incentive = BigDecimal.ZERO;

    // ============ GROSS SALARY ============
    @Column(precision = 12, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;      // GROSS SALARY

    // ============ DEDUCTIONS ============
    @Column(precision = 12, scale = 2)
    private BigDecimal esiEmployee = BigDecimal.ZERO;      // ESI 0.75%

    @Column(precision = 12, scale = 2)
    private BigDecimal pfEmployee = BigDecimal.ZERO;       // PF OWN 6%

    @Column(precision = 12, scale = 2)
    private BigDecimal pfCompany = BigDecimal.ZERO;        // PF COMPANY CONT. 6%

    @Column(precision = 12, scale = 2)
    private BigDecimal advance = BigDecimal.ZERO;          // ADV (Advance deduction)

    @Column(precision = 12, scale = 2)
    private BigDecimal due = BigDecimal.ZERO;              // DUE (Previous dues)

    @Column(precision = 12, scale = 2)
    private BigDecimal loanDeduction = BigDecimal.ZERO;    // Fixed EMI Loan deduction
    
    @Column(precision = 12, scale = 2)
    private BigDecimal flexibleLoanDeduction = BigDecimal.ZERO;  // Admin-adjusted flexible loan deduction
    
    @Column(precision = 12, scale = 2)
    private BigDecimal scheduledLoanAmount = BigDecimal.ZERO;    // Original scheduled loan amount (before adjustment)
    
    @Column(precision = 12, scale = 2)
    private BigDecimal adjustedLoanAmount = BigDecimal.ZERO;     // Actual loan amount deducted (after smart adjustment)

    @Column(precision = 12, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal tds = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    // ============ NET SALARY ============
    @Column(precision = 12, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;        // NET SALARY

    // ============ PAYMENT DETAILS ============
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;       // BANK, UPI, CASH, CHEQUE

    private String bankName;
    private String bankAccount;
    private String ifscCode;
    private String transactionReference;
    private String upiId;
    private String chequeNumber;

    private LocalDate processedDate;
    private LocalDate paidDate;
    private LocalDateTime paymentTimestamp;

    @Column(length = 500)
    private String remarks;                // REMARKS

    // ============ AUDIT ============
    private String createdBy;
    private String approvedBy;
    private String paidBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Payroll() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Calculate all totals based on the payment sheet formula from Excel:
     * GROSS SALARY = WORKING DAY AMOUNT + OT DAY AMOUNT + OT HR AMOUNT + HOUSE RENT + MEDICAL EXP + BONUS + INCENTIVE
     * 
     * Excel formula for NET SALARY: =GROSS - ESI - PF_Own - PF_Company - ADV + DUE
     * Where:
     *   - ESI, PF_Own, PF_Company, ADV are DEDUCTED
     *   - DUE is ADDED (dues owed TO employee from previous periods)
     * 
     * ADV column should include loanDeduction + manual advance.
     * If advance < loanDeduction (legacy data), we use the greater value to ensure loan is deducted.
     */
    public void calculateTotals() {
        // Calculate gross salary
        // GROSS = WORKING DAY AMOUNT + OT DAY AMOUNT + OT HR AMOUNT + PAID LEAVE CHARGES + ALLOWANCES + BONUS + INCENTIVE
        this.grossSalary = safeAdd(workingDayAmount)
                .add(safeAdd(overtimeDayAmount))
                .add(safeAdd(overtimeHourAmount))
                .add(safeAdd(paidLeaveCharges))  // Paid leave charges added to gross
                .add(safeAdd(houseRent))
                .add(safeAdd(medicalExpense))
                .add(safeAdd(conveyanceAllowance))
                .add(safeAdd(specialAllowance))
                .add(safeAdd(otherAllowance))
                .add(safeAdd(bonus))
                .add(safeAdd(incentive));

        // Calculate total loan deductions (fixed EMI + flexible loan deductions)
        BigDecimal totalLoanDeductions = safeAdd(loanDeduction).add(safeAdd(flexibleLoanDeduction));
        
        // Ensure advance includes at least the total loan deductions
        BigDecimal effectiveAdvance = safeAdd(advance);
        if (effectiveAdvance.compareTo(totalLoanDeductions) < 0) {
            effectiveAdvance = totalLoanDeductions;
            this.advance = totalLoanDeductions;
        }

        // Calculate other deductions (excluding advance/loan)
        BigDecimal otherDeductions = safeAdd(esiEmployee)
                .add(safeAdd(pfEmployee))
                .add(safeAdd(pfCompany))
                .add(safeAdd(lateHourCharges))
                .add(safeAdd(earlyHourCharges))
                .add(safeAdd(professionalTax))
                .add(safeAdd(tds))
                .add(safeAdd(otherDeduction));

        // SMART LOAN ADJUSTMENT: Cap loan deduction to prevent negative net salary
        // Calculate maximum loan that can be deducted: GROSS - Other Deductions + DUE
        BigDecimal maxLoanDeductible = grossSalary
                .subtract(otherDeductions)
                .add(safeAdd(due))
                .max(BigDecimal.ZERO); // Ensure non-negative
        
        // Store scheduled loan amount (before adjustment)
        // scheduledLoanAmount is set during calculateDeductions() from getMonthlyEmiDeduction()
        BigDecimal scheduledLoan = safeAdd(this.scheduledLoanAmount);
        if (scheduledLoan.compareTo(BigDecimal.ZERO) == 0) {
            // If scheduledLoanAmount not set, use effectiveAdvance (which includes loanEmi)
            scheduledLoan = effectiveAdvance;
            this.scheduledLoanAmount = effectiveAdvance;
        }
        
        // SMART ADJUSTMENT: If scheduled loan exceeds max deductible, cap it
        // Example: Scheduled ₹5,000, Max Deductible ₹1,953 → Adjusted ₹1,953
        BigDecimal adjustedLoanDeduction = effectiveAdvance.min(maxLoanDeductible);
        BigDecimal loanAdjustment = effectiveAdvance.subtract(adjustedLoanDeduction);
        
        // Store adjustment info for loan management update
        this.adjustedLoanAmount = adjustedLoanDeduction;
        
        // Always update scheduledLoanAmount if we have a value
        if (scheduledLoan.compareTo(BigDecimal.ZERO) > 0) {
            this.scheduledLoanAmount = scheduledLoan;
        }
        
        // If loan was adjusted (scheduled > adjusted), update advance to adjusted amount
        // This ensures net salary doesn't go negative
        if (loanAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            // Loan was adjusted - store the actual deducted amount
            effectiveAdvance = adjustedLoanDeduction;
            this.advance = adjustedLoanDeduction;
            // Note: loanAdjustment amount (₹3,047) remains as outstanding balance in loan management
            // This will be deducted in future payrolls when net salary allows
        }

        // Calculate total deductions (for display purposes)
        // Formula: ESI + PF Employee + PF Company + ADV (adjusted) + Late Hour Charges + Early Hour Charges + Professional Tax + TDS + Other Deductions
        this.totalDeductions = otherDeductions
                .add(effectiveAdvance); // Use adjusted advance

        // Calculate net salary using Excel formula:
        // NET = GROSS - ESI - PF_Own - PF_Company - ADV (adjusted) + DUE
        // DUE is ADDED (money owed TO employee)
        this.netSalary = grossSalary
                .subtract(totalDeductions)   // Subtract all deductions (with adjusted loan)
                .add(safeAdd(due));          // ADD due (money owed to employee)
        
        // Store loan adjustment info for loan management update
        if (loanAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            // This will be used to update loan balance in loan management
            // The difference (loanAdjustment) remains as outstanding balance
        }
        
        // Auto-generate REMARKS with calculation breakdown (if not manually set)
        // Format: GROSS - ADV + DUE = NET (only shows non-zero components)
        if (this.remarks == null || this.remarks.isEmpty() || this.remarks.startsWith("Auto:")) {
            this.remarks = generateRemarksBreakdown(effectiveAdvance);
        }
        
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Generate remarks showing payment breakdown
     * Format examples:
     *   - "8196" (no deductions/dues)
     *   - "12093 -4900" (with advance)
     *   - "8196 +500" (with dues)
     *   - "12093 -4900 +500" (with both)
     */
    private String generateRemarksBreakdown(BigDecimal effectiveAdvance) {
        StringBuilder sb = new StringBuilder();
        
        // Start with gross
        sb.append(grossSalary != null ? grossSalary.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() : "0");
        
        // Subtract advance if > 0
        if (effectiveAdvance != null && effectiveAdvance.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" -").append(effectiveAdvance.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        }
        
        // Add due if > 0
        BigDecimal dueAmount = safeAdd(due);
        if (dueAmount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" +").append(dueAmount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        }
        
        return sb.toString();
    }

    private BigDecimal safeAdd(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ============ GETTERS AND SETTERS ============
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

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }

    public BigDecimal getBasicSalary() { return basicSalary; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }

    public BigDecimal getFinalPayment() { return finalPayment; }
    public void setFinalPayment(BigDecimal finalPayment) { this.finalPayment = finalPayment; }

    public Integer getTotalWorkingDays() { return totalWorkingDays; }
    public void setTotalWorkingDays(Integer totalWorkingDays) { this.totalWorkingDays = totalWorkingDays; }

    public Integer getPresentDays() { return presentDays; }
    public void setPresentDays(Integer presentDays) { this.presentDays = presentDays; }

    public Integer getAbsentDays() { return absentDays; }
    public void setAbsentDays(Integer absentDays) { this.absentDays = absentDays; }

    public Integer getPaidLeaveDays() { return paidLeaveDays; }
    public void setPaidLeaveDays(Integer paidLeaveDays) { this.paidLeaveDays = paidLeaveDays; }

    public Integer getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public void setUnpaidLeaveDays(Integer unpaidLeaveDays) { this.unpaidLeaveDays = unpaidLeaveDays; }

    public Integer getHalfDays() { return halfDays; }
    public void setHalfDays(Integer halfDays) { this.halfDays = halfDays; }

    public Integer getWeeklyOffDays() { return weeklyOffDays; }
    public void setWeeklyOffDays(Integer weeklyOffDays) { this.weeklyOffDays = weeklyOffDays; }

    public Integer getHolidayDays() { return holidayDays; }
    public void setHolidayDays(Integer holidayDays) { this.holidayDays = holidayDays; }

    public Integer getLateDays() { return lateDays; }
    public void setLateDays(Integer lateDays) { this.lateDays = lateDays; }

    public Integer getLateDeductionDays() { return lateDeductionDays; }
    public void setLateDeductionDays(Integer lateDeductionDays) { this.lateDeductionDays = lateDeductionDays; }

    public BigDecimal getTotalLateHours() { return totalLateHours; }
    public void setTotalLateHours(BigDecimal totalLateHours) { this.totalLateHours = totalLateHours; }

    public BigDecimal getLateHourCharges() { return lateHourCharges; }
    public void setLateHourCharges(BigDecimal lateHourCharges) { this.lateHourCharges = lateHourCharges; }

    public Integer getEarlyOutDays() { return earlyOutDays; }
    public void setEarlyOutDays(Integer earlyOutDays) { this.earlyOutDays = earlyOutDays; }

    public BigDecimal getTotalEarlyHours() { return totalEarlyHours; }
    public void setTotalEarlyHours(BigDecimal totalEarlyHours) { this.totalEarlyHours = totalEarlyHours; }

    public BigDecimal getEarlyHourCharges() { return earlyHourCharges; }
    public void setEarlyHourCharges(BigDecimal earlyHourCharges) { this.earlyHourCharges = earlyHourCharges; }

    public BigDecimal getWorkingDayAmount() { return workingDayAmount; }
    public void setWorkingDayAmount(BigDecimal workingDayAmount) { this.workingDayAmount = workingDayAmount; }

    public Integer getOvertimeDays() { return overtimeDays; }
    public void setOvertimeDays(Integer overtimeDays) { this.overtimeDays = overtimeDays; }

    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(BigDecimal overtimeHours) { this.overtimeHours = overtimeHours; }

    public BigDecimal getOvertimeDayAmount() { return overtimeDayAmount; }
    public void setOvertimeDayAmount(BigDecimal overtimeDayAmount) { this.overtimeDayAmount = overtimeDayAmount; }

    public BigDecimal getOvertimeHourAmount() { return overtimeHourAmount; }
    public void setOvertimeHourAmount(BigDecimal overtimeHourAmount) { this.overtimeHourAmount = overtimeHourAmount; }
    
    public BigDecimal getPaidLeaveCharges() { return paidLeaveCharges; }
    public void setPaidLeaveCharges(BigDecimal paidLeaveCharges) { this.paidLeaveCharges = paidLeaveCharges; }

    public BigDecimal getHouseRent() { return houseRent; }
    public void setHouseRent(BigDecimal houseRent) { this.houseRent = houseRent; }

    public BigDecimal getMedicalExpense() { return medicalExpense; }
    public void setMedicalExpense(BigDecimal medicalExpense) { this.medicalExpense = medicalExpense; }

    public BigDecimal getConveyanceAllowance() { return conveyanceAllowance; }
    public void setConveyanceAllowance(BigDecimal conveyanceAllowance) { this.conveyanceAllowance = conveyanceAllowance; }

    public BigDecimal getSpecialAllowance() { return specialAllowance; }
    public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }

    public BigDecimal getOtherAllowance() { return otherAllowance; }
    public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }

    public BigDecimal getBonus() { return bonus; }
    public void setBonus(BigDecimal bonus) { this.bonus = bonus; }

    public BigDecimal getIncentive() { return incentive; }
    public void setIncentive(BigDecimal incentive) { this.incentive = incentive; }

    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }

    public BigDecimal getEsiEmployee() { return esiEmployee; }
    public void setEsiEmployee(BigDecimal esiEmployee) { this.esiEmployee = esiEmployee; }

    public BigDecimal getPfEmployee() { return pfEmployee; }
    public void setPfEmployee(BigDecimal pfEmployee) { this.pfEmployee = pfEmployee; }

    public BigDecimal getPfCompany() { return pfCompany; }
    public void setPfCompany(BigDecimal pfCompany) { this.pfCompany = pfCompany; }

    public BigDecimal getAdvance() { return advance; }
    public void setAdvance(BigDecimal advance) { this.advance = advance; }

    public BigDecimal getDue() { return due; }
    public void setDue(BigDecimal due) { this.due = due; }

    public BigDecimal getLoanDeduction() { return loanDeduction; }
    public void setLoanDeduction(BigDecimal loanDeduction) { this.loanDeduction = loanDeduction; }
    
    public BigDecimal getFlexibleLoanDeduction() { return flexibleLoanDeduction; }
    public void setFlexibleLoanDeduction(BigDecimal flexibleLoanDeduction) { this.flexibleLoanDeduction = flexibleLoanDeduction; }
    
    public BigDecimal getScheduledLoanAmount() { return scheduledLoanAmount; }
    public void setScheduledLoanAmount(BigDecimal scheduledLoanAmount) { this.scheduledLoanAmount = scheduledLoanAmount; }
    
    public BigDecimal getAdjustedLoanAmount() { return adjustedLoanAmount; }
    public void setAdjustedLoanAmount(BigDecimal adjustedLoanAmount) { this.adjustedLoanAmount = adjustedLoanAmount; }

    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }

    public BigDecimal getTds() { return tds; }
    public void setTds(BigDecimal tds) { this.tds = tds; }

    public BigDecimal getOtherDeduction() { return otherDeduction; }
    public void setOtherDeduction(BigDecimal otherDeduction) { this.otherDeduction = otherDeduction; }

    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }

    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }

    public PayrollStatus getStatus() { return status; }
    public void setStatus(PayrollStatus status) { this.status = status; }

    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String chequeNumber) { this.chequeNumber = chequeNumber; }

    public LocalDate getProcessedDate() { return processedDate; }
    public void setProcessedDate(LocalDate processedDate) { this.processedDate = processedDate; }

    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }

    public LocalDateTime getPaymentTimestamp() { return paymentTimestamp; }
    public void setPaymentTimestamp(LocalDateTime paymentTimestamp) { this.paymentTimestamp = paymentTimestamp; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getPaidBy() { return paidBy; }
    public void setPaidBy(String paidBy) { this.paidBy = paidBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
