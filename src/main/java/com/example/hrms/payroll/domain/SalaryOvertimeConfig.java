package com.example.hrms.payroll.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration for salary calculation and overtime rules.
 * Each tenant can have their own configuration.
 * 
 * Key Features:
 * 1. Full Month Salary Threshold - If employee works >= X days, they get full month salary
 * 2. Overtime Minimum Threshold - Minimum minutes after shift end to count as OT
 * 3. Overtime Multipliers - Different rates for regular OT, holiday OT, etc.
 * 4. Per Day Calculation - How to calculate per-day salary (e.g., salary/30 or salary/actual_days)
 */
@Entity
@Table(name = "salary_overtime_config",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id"}),
       indexes = @Index(name = "idx_salary_config_tenant", columnList = "tenant_id"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryOvertimeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    // ==================== SALARY CALCULATION RULES ====================

    /**
     * Minimum working days required to get full month salary.
     * Example: If set to 28, employee working 28+ days gets full 30-day salary.
     * Default: 30 (no threshold, pay as per actual days)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer fullMonthSalaryThresholdDays = 30;

    /**
     * Number of days considered as a full month for salary calculation.
     * Most companies use 30 regardless of actual days in month.
     * Default: 30
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer salaryCalculationDaysInMonth = 30;

    /**
     * Standard working hours per day (used for OT hourly rate calculation).
     * Default: 8 hours
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer standardWorkingHoursPerDay = 8;

    /**
     * Whether to pay full salary if employee works >= threshold days.
     * If false, always pay proportional to days worked.
     * Default: true
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enableFullMonthSalaryThreshold = true;

    // ==================== OVERTIME RULES ====================

    /**
     * Minimum minutes worked after shift end to be counted as overtime.
     * Example: If set to 30, working 29 mins extra = no OT, 30 mins = OT starts.
     * Default: 30 minutes
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer overtimeMinThresholdMins = 30;

    /**
     * Whether overtime is enabled.
     * Default: true
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean overtimeEnabled = true;

    /**
     * Regular overtime multiplier (e.g., 1.0 = normal rate, 1.5 = 1.5x, 2.0 = double).
     * Applied for overtime on regular working days.
     * Default: 1.0 (normal rate - one day OT = 1/30 of monthly salary * hours/8)
     */
    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal regularOvertimeMultiplier = new BigDecimal("1.0");

    /**
     * Weekend overtime multiplier.
     * Applied for overtime on weekly offs (Saturday/Sunday).
     * Default: 1.5
     */
    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal weekendOvertimeMultiplier = new BigDecimal("1.5");

    /**
     * Holiday overtime multiplier.
     * Applied for overtime on declared holidays.
     * Default: 2.0 (double pay)
     */
    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal holidayOvertimeMultiplier = new BigDecimal("2.0");

    /**
     * Maximum overtime hours allowed per day.
     * Any hours beyond this will not be counted.
     * Default: 4 hours (240 minutes)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxOvertimeHoursPerDay = 4;

    /**
     * Maximum overtime hours allowed per month.
     * Default: 50 hours
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxOvertimeHoursPerMonth = 50;

    /**
     * How overtime is calculated:
     * HOURLY - Based on hours worked (salary/30/8 * hours * multiplier)
     * DAILY - Based on days worked (salary/30 * days * multiplier)
     * Default: HOURLY
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OvertimeCalculationType overtimeCalculationType = OvertimeCalculationType.HOURLY;

    // ==================== LATE/EARLY DEDUCTION RULES ====================

    /**
     * Number of late arrivals that count as one absent day.
     * Example: 3 means 3 late marks = 1 day deduction.
     * Default: 3
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer lateArrivalsPerAbsent = 3;

    /**
     * Whether to deduct salary for late arrivals.
     * Default: true
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deductForLateArrival = true;

    /**
     * Grace period for late arrival (minutes).
     * This is additional to shift's grace_in_mins.
     * Default: 0 (use shift's grace period only)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer lateArrivalGraceMins = 0;

    // ==================== HALF DAY RULES ====================

    /**
     * Minimum hours worked to count as half day.
     * Default: 4 hours
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer halfDayMinHours = 4;

    /**
     * Minimum hours worked to count as full day.
     * Default: 7 hours (considering 1 hour break in 8 hour shift)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer fullDayMinHours = 7;

    // ==================== STATUTORY DEDUCTION RATES ====================

    /**
     * ESI (Employee State Insurance) employee contribution rate.
     * Standard rate is 0.75% of gross salary.
     * Default: 0.75%
     */
    @Column(nullable = false, precision = 6, scale = 4)
    @Builder.Default
    private BigDecimal esiEmployeeRate = new BigDecimal("0.0075");

    /**
     * ESI employer contribution rate.
     * Standard rate is 3.25% of gross salary.
     * Default: 3.25%
     */
    @Column(nullable = false, precision = 6, scale = 4)
    @Builder.Default
    private BigDecimal esiEmployerRate = new BigDecimal("0.0325");

    /**
     * ESI wage ceiling - employees with gross > this are exempt.
     * As of 2024, the limit is ₹21,000/month.
     * Default: 21000
     */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal esiWageCeiling = new BigDecimal("21000");

    /**
     * EPF (Employee Provident Fund) employee contribution rate.
     * Standard rate is 12% of basic + DA (or 6% of gross in some cases).
     * Default: 6% (as per payment sheet)
     */
    @Column(nullable = false, precision = 6, scale = 4)
    @Builder.Default
    private BigDecimal pfEmployeeRate = new BigDecimal("0.06");

    /**
     * EPF employer contribution rate.
     * Standard rate is 12% (3.67% to EPF + 8.33% to EPS).
     * Default: 6% (as per payment sheet)
     */
    @Column(nullable = false, precision = 6, scale = 4)
    @Builder.Default
    private BigDecimal pfEmployerRate = new BigDecimal("0.06");

    /**
     * PF wage ceiling - basic salary cap for PF calculation.
     * As of 2024, the statutory limit is ₹15,000/month for employer contribution.
     * Default: 15000
     */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal pfWageCeiling = new BigDecimal("15000");

    /**
     * Whether PF is calculated on basic salary or full payment.
     * BASIC - Calculate on basic salary only
     * FULL_PAYMENT - Calculate on basic + increment (FINAL PAYMENT)
     * Default: FULL_PAYMENT (as per payment sheet)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PfCalculationBase pfCalculationBase = PfCalculationBase.FULL_PAYMENT;

    /**
     * Professional Tax (PT) monthly amount.
     * Varies by state. 0 means not applicable.
     * Default: 0
     */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal professionalTaxAmount = BigDecimal.ZERO;

    // ==================== METADATA ====================

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Overtime calculation type enum.
     */
    public enum OvertimeCalculationType {
        HOURLY,  // Calculate OT based on hours worked
        DAILY    // Calculate OT based on days worked
    }

    /**
     * PF calculation base enum.
     */
    public enum PfCalculationBase {
        BASIC,        // Calculate PF on basic salary only
        FULL_PAYMENT  // Calculate PF on basic + increment (FINAL PAYMENT)
    }
}
