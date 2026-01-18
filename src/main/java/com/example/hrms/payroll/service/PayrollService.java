package com.example.hrms.payroll.service;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.domain.Employee;
import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.loan.service.LoanService;
import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import com.example.hrms.payroll.domain.enums.PaymentMode;
import com.example.hrms.payroll.domain.enums.PayrollStatus;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.WeeklyOffConfigRepository;
import com.example.hrms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Payroll Service implementing the exact payment sheet calculation:
 * 
 * WORKING DAY AMOUNT = (FINAL PAYMENT / WORKING DAYS) * PRESENT DAYS
 * OT DAY AMOUNT = (FINAL PAYMENT / WORKING DAYS) * OT DAYS
 * OT HR AMOUNT = (FINAL PAYMENT / WORKING DAYS / 8) * OT HOURS
 * GROSS SALARY = WORKING DAY AMOUNT + OT DAY AMOUNT + OT HR AMOUNT + HOUSE RENT + MEDICAL
 * ESI = GROSS SALARY * 0.75%
 * PF OWN = FINAL PAYMENT * 6% (based on monthly salary, not prorated)
 * NET SALARY = GROSS SALARY - ESI - PF OWN - ADV - DUE
 */
@Service
@Transactional
public class PayrollService {

    private final PayrollRepository payrollRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceDayRepository attendanceDayRepo;
    private final HolidayRepository holidayRepo;
    private final WeeklyOffConfigRepository weeklyOffRepo;
    private final LoanService loanService;
    private final SalaryOvertimeConfigService configService;

    // Standard deduction rates matching payment sheet
    private static final BigDecimal ESI_RATE = new BigDecimal("0.0075");  // 0.75%
    private static final BigDecimal PF_RATE = new BigDecimal("0.06");     // 6%

    public PayrollService(PayrollRepository payrollRepo,
                          EmployeeRepository employeeRepo,
                          AttendanceDayRepository attendanceDayRepo,
                          HolidayRepository holidayRepo,
                          WeeklyOffConfigRepository weeklyOffRepo,
                          LoanService loanService,
                          SalaryOvertimeConfigService configService) {
        this.payrollRepo = payrollRepo;
        this.employeeRepo = employeeRepo;
        this.attendanceDayRepo = attendanceDayRepo;
        this.holidayRepo = holidayRepo;
        this.weeklyOffRepo = weeklyOffRepo;
        this.loanService = loanService;
        this.configService = configService;
    }

    /**
     * Generate payroll for a single employee for a month
     */
    public Payroll generatePayroll(String orgId, String empId, int year, int month) {
        // Use tenant-aware employee lookup to avoid cross-tenant issues
        String tenantId = TenantContext.getTenantId();
        Employee emp = (tenantId != null 
                ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empId)
                : employeeRepo.findByEmpCode(empId))
            .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + empId));

        // Check if payroll already exists
        Optional<Payroll> existing = payrollRepo.findByOrgIdAndEmpIdAndYearAndMonth(orgId, empId, year, month);
        Payroll payroll = existing.orElseGet(Payroll::new);

        payroll.setOrgId(orgId);
        payroll.setEmpId(empId);
        payroll.setEmpName(getEmployeeName(emp));
        payroll.setYear(year);
        payroll.setMonth(month);
        payroll.setStatus(PayrollStatus.DRAFT);

        // Step 1: Set salary structure
        setSalaryStructure(payroll, emp);

        // Step 2: Calculate attendance summary
        calculateAttendanceSummary(payroll, emp, year, month);

        // Step 3: Calculate earnings (working day amount, OT, allowances)
        calculateEarnings(payroll, emp);

        // Step 4: Calculate deductions (ESI, PF, Advance, Loan)
        calculateDeductions(payroll, emp);

        // Step 5: Calculate totals
        payroll.calculateTotals();
        payroll.setProcessedDate(LocalDate.now());

        return payrollRepo.save(payroll);
    }

    /**
     * Set salary structure from employee master
     */
    private void setSalaryStructure(Payroll payroll, Employee emp) {
        BigDecimal basic = emp.getBaseSalary() != null ? emp.getBaseSalary() : BigDecimal.ZERO;
        BigDecimal increment = emp.getIncrement() != null ? emp.getIncrement() : BigDecimal.ZERO;
        
        payroll.setBasicSalary(basic);
        payroll.setIncrement(increment);
        payroll.setFinalPayment(basic.add(increment));
    }

    /**
     * Calculate attendance summary for the month
     */
    private void calculateAttendanceSummary(Payroll payroll, Employee emp, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        // Get holidays for the month
        List<Holiday> holidays = holidayRepo.findByOrgIdAndHolidayDateBetweenAndActiveTrue(
                payroll.getOrgId(), startDate, endDate);
        Set<LocalDate> holidayDates = new HashSet<>();
        for (Holiday h : holidays) {
            if (h.appliesToEmploymentType(emp.getEmploymentType())) {
                holidayDates.add(h.getHolidayDate());
            }
        }

        // Get weekly off configuration
        Set<DayOfWeek> weeklyOffDays = getWeeklyOffDays(payroll.getOrgId(), emp);

        // Get attendance data
        List<AttendanceDay> attendanceDays = attendanceDayRepo.findByEmployeeIdAndWorkDateBetween(
                emp.getId(), startDate, endDate);
        Map<LocalDate, AttendanceDay> attendanceMap = new HashMap<>();
        for (AttendanceDay ad : attendanceDays) {
            attendanceMap.put(ad.getWorkDate(), ad);
        }

        int totalWorkingDays = 0;
        int presentDays = 0;
        int absentDays = 0;
        int halfDays = 0;
        int weeklyOffCount = 0;
        int holidayCount = 0;
        int lateDays = 0;
        int overtimeDays = 0;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;

        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            boolean isWeeklyOff = weeklyOffDays.contains(date.getDayOfWeek());
            boolean isHoliday = holidayDates.contains(date);

            if (isWeeklyOff) {
                weeklyOffCount++;
                // Check for OT on weekly off
                AttendanceDay ad = attendanceMap.get(date);
                if (ad != null && ad.getTotalWorkMin() != null && ad.getTotalWorkMin() > 0) {
                    overtimeDays++;
                    BigDecimal hours = BigDecimal.valueOf(ad.getTotalWorkMin())
                            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                    totalOvertimeHours = totalOvertimeHours.add(hours);
                }
            } else if (isHoliday) {
                holidayCount++;
                // Check for OT on holiday
                AttendanceDay ad = attendanceMap.get(date);
                if (ad != null && ad.getTotalWorkMin() != null && ad.getTotalWorkMin() > 0) {
                    overtimeDays++;
                    BigDecimal hours = BigDecimal.valueOf(ad.getTotalWorkMin())
                            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                    totalOvertimeHours = totalOvertimeHours.add(hours);
                }
            } else {
                totalWorkingDays++;

                AttendanceDay ad = attendanceMap.get(date);
                if (ad != null) {
                    String status = ad.getStatus() != null ? ad.getStatus().toUpperCase() : "";
                    
                    if ("PRESENT".equals(status)) {
                        presentDays++;
                        
                        // Check for late
                        if (ad.getLateByMins() != null && ad.getLateByMins() > 0) {
                            lateDays++;
                        }
                        
                        // Check for OT on working day using configurable thresholds
                        if (ad.getTotalWorkMin() != null) {
                            SalaryOvertimeConfig otConfig = configService.getConfig();
                            int standardMins = otConfig.getStandardWorkingHoursPerDay() * 60;
                            int otMinThreshold = otConfig.getOvertimeMinThresholdMins();
                            int extraMins = ad.getTotalWorkMin() - standardMins;
                            
                            // Only count as OT if extra minutes exceed the minimum threshold
                            // e.g., if threshold is 30 mins, working 29 mins extra = no OT
                            if (extraMins >= otMinThreshold && otConfig.getOvertimeEnabled()) {
                                BigDecimal otMins = BigDecimal.valueOf(extraMins);
                                totalOvertimeHours = totalOvertimeHours.add(
                                        otMins.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                            }
                        }
                    } else if ("PARTIAL".equals(status) || "HALF_DAY".equals(status)) {
                        halfDays++;
                    } else if ("LEAVE".equals(status)) {
                        // Will be counted separately
                    } else {
                        absentDays++;
                    }
                } else {
                    absentDays++;
                }
            }
            date = date.plusDays(1);
        }

        // Calculate late deduction days (configurable: X lates = 1 absent)
        SalaryOvertimeConfig config = configService.getConfig();
        int lateToAbsentCount = config.getLateArrivalsPerAbsent();
        int lateDeductionDays = lateToAbsentCount > 0 ? lateDays / lateToAbsentCount : 0;

        payroll.setTotalWorkingDays(totalWorkingDays);
        payroll.setPresentDays(presentDays);
        payroll.setAbsentDays(absentDays);
        payroll.setHalfDays(halfDays);
        payroll.setWeeklyOffDays(weeklyOffCount);
        payroll.setHolidayDays(holidayCount);
        payroll.setLateDays(lateDays);
        payroll.setLateDeductionDays(lateDeductionDays);
        payroll.setOvertimeDays(overtimeDays);
        payroll.setOvertimeHours(totalOvertimeHours);
        payroll.setPaidLeaveDays(0);
        payroll.setUnpaidLeaveDays(0);
    }

    /**
     * Calculate earnings based on payment sheet formula with configurable rules.
     * 
     * Uses SalaryOvertimeConfig for:
     * - Full month salary threshold (e.g., 28 days worked = full 30 day salary)
     * - OT multipliers (regular, weekend, holiday)
     * - Days in month for calculation (typically 30)
     * - Standard working hours per day
     */
    private void calculateEarnings(Payroll payroll, Employee emp) {
        // Get configuration for the tenant
        SalaryOvertimeConfig config = configService.getConfig();
        
        BigDecimal finalPayment = payroll.getFinalPayment();
        int actualWorkingDays = payroll.getTotalWorkingDays() != null ? payroll.getTotalWorkingDays() : 28;
        int presentDays = payroll.getPresentDays() != null ? payroll.getPresentDays() : 0;
        int halfDays = payroll.getHalfDays() != null ? payroll.getHalfDays() : 0;
        
        if (actualWorkingDays == 0) actualWorkingDays = 28; // Fallback
        
        // Get configured values
        int salaryDaysInMonth = config.getSalaryCalculationDaysInMonth(); // e.g., 30
        int fullMonthThreshold = config.getFullMonthSalaryThresholdDays(); // e.g., 28
        int standardHoursPerDay = config.getStandardWorkingHoursPerDay(); // e.g., 8
        boolean thresholdEnabled = config.getEnableFullMonthSalaryThreshold();

        // Calculate payable days (present + half days * 0.5)
        BigDecimal payableDays = BigDecimal.valueOf(presentDays)
                .add(BigDecimal.valueOf(halfDays).multiply(new BigDecimal("0.5")));

        // Apply full month salary threshold logic:
        // If employee worked >= threshold days, they get full month salary
        BigDecimal effectiveDays;
        if (thresholdEnabled && presentDays >= fullMonthThreshold) {
            // Employee qualifies for full month salary
            effectiveDays = BigDecimal.valueOf(salaryDaysInMonth);
        } else {
            // Pay based on actual days worked
            effectiveDays = payableDays;
        }

        // Per day rate = FINAL PAYMENT / DAYS IN MONTH (configurable, typically 30)
        BigDecimal perDayRate = finalPayment.divide(BigDecimal.valueOf(salaryDaysInMonth), 4, RoundingMode.HALF_UP);
        
        // Per hour rate = Per day rate / STANDARD HOURS (configurable, typically 8)
        BigDecimal perHourRate = perDayRate.divide(BigDecimal.valueOf(standardHoursPerDay), 4, RoundingMode.HALF_UP);

        // WORKING DAY AMOUNT = Per day rate * Effective days
        BigDecimal workingDayAmount = perDayRate.multiply(effectiveDays).setScale(0, RoundingMode.HALF_UP);
        payroll.setWorkingDayAmount(workingDayAmount);

        // OT DAY AMOUNT = Per day rate * OT days * Multiplier (for full day OT on holidays/weekends)
        int otDays = payroll.getOvertimeDays() != null ? payroll.getOvertimeDays() : 0;
        BigDecimal weekendMultiplier = config.getWeekendOvertimeMultiplier();
        BigDecimal otDayAmount = perDayRate
                .multiply(BigDecimal.valueOf(otDays))
                .multiply(weekendMultiplier) // Apply OT multiplier
                .setScale(0, RoundingMode.HALF_UP);
        payroll.setOvertimeDayAmount(otDayAmount);

        // OT HR AMOUNT = Per hour rate * OT hours * Multiplier (for extra hours on working days)
        BigDecimal otHours = payroll.getOvertimeHours() != null ? payroll.getOvertimeHours() : BigDecimal.ZERO;
        BigDecimal regularMultiplier = config.getRegularOvertimeMultiplier();
        BigDecimal otHourAmount = perHourRate
                .multiply(otHours)
                .multiply(regularMultiplier)
                .setScale(0, RoundingMode.HALF_UP);
        payroll.setOvertimeHourAmount(otHourAmount);

        // Set allowances from employee master
        payroll.setHouseRent(emp.getHraPercent() != null ? 
                finalPayment.multiply(emp.getHraPercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        payroll.setMedicalExpense(emp.getMedicalAllowance() != null ? emp.getMedicalAllowance() : BigDecimal.ZERO);
        payroll.setConveyanceAllowance(emp.getConveyanceAllowance() != null ? emp.getConveyanceAllowance() : BigDecimal.ZERO);
        payroll.setSpecialAllowance(emp.getSpecialAllowance() != null ? emp.getSpecialAllowance() : BigDecimal.ZERO);
        payroll.setOtherAllowance(emp.getOtherAllowance() != null ? emp.getOtherAllowance() : BigDecimal.ZERO);
    }

    /**
     * Calculate deductions based on payment sheet formula
     */
    private void calculateDeductions(Payroll payroll, Employee emp) {
        // First calculate gross to determine ESI
        BigDecimal grossSalary = safeAdd(payroll.getWorkingDayAmount())
                .add(safeAdd(payroll.getOvertimeDayAmount()))
                .add(safeAdd(payroll.getOvertimeHourAmount()))
                .add(safeAdd(payroll.getHouseRent()))
                .add(safeAdd(payroll.getMedicalExpense()))
                .add(safeAdd(payroll.getConveyanceAllowance()))
                .add(safeAdd(payroll.getSpecialAllowance()))
                .add(safeAdd(payroll.getOtherAllowance()));

        // ESI = GROSS SALARY * 0.75% (only if ESI applicable)
        if (Boolean.TRUE.equals(emp.getEsicApplicable())) {
            BigDecimal esi = grossSalary.multiply(ESI_RATE).setScale(0, RoundingMode.CEILING);
            payroll.setEsiEmployee(esi);
        }

        // PF = FINAL PAYMENT * 6% (based on monthly salary, not prorated)
        if (Boolean.TRUE.equals(emp.getEpfApplicable())) {
            BigDecimal pfOwn = payroll.getFinalPayment().multiply(PF_RATE).setScale(4, RoundingMode.HALF_UP);
            BigDecimal pfCompany = payroll.getFinalPayment().multiply(PF_RATE).setScale(4, RoundingMode.HALF_UP);
            payroll.setPfEmployee(pfOwn);
            payroll.setPfCompany(pfCompany);
        }

        // Loan deduction from loan module
        BigDecimal loanEmi = loanService.getMonthlyEmiDeduction(payroll.getOrgId(), payroll.getEmpId());
        payroll.setLoanDeduction(loanEmi); // Keep for detailed reporting

        // Professional Tax (if applicable)
        if (Boolean.TRUE.equals(emp.getPtApplicable()) && grossSalary.compareTo(new BigDecimal("10000")) > 0) {
            payroll.setProfessionalTax(new BigDecimal("200"));
        }

        // ADV column in payment sheet = Loan EMI + any manual advance given
        // The payment sheet format expects ADV to contain all advance-type deductions
        // including loan EMI deductions
        BigDecimal existingAdvance = payroll.getAdvance();
        if (existingAdvance == null || existingAdvance.compareTo(BigDecimal.ZERO) == 0) {
            // No manual advance, set advance = loan EMI
            payroll.setAdvance(loanEmi);
        } else {
            // If there's already a manual advance set, add loan EMI to it
            // But only if the current advance doesn't already include the loan EMI
            // (to handle regeneration scenarios)
            if (payroll.getLoanDeduction() != null && 
                existingAdvance.compareTo(payroll.getLoanDeduction()) != 0) {
                // Advance was manually set, add loan EMI
                payroll.setAdvance(existingAdvance.add(loanEmi));
            }
        }
        
        // Due column is for previous month's dues (set separately via API)
        if (payroll.getDue() == null) payroll.setDue(BigDecimal.ZERO);
    }

    private BigDecimal safeAdd(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Set<DayOfWeek> getWeeklyOffDays(String orgId, Employee emp) {
        Set<DayOfWeek> weeklyOffDays = new HashSet<>();
        
        // First check employee-level weekly off
        if (emp.getWeeklyOffDays() != null && !emp.getWeeklyOffDays().isEmpty()) {
            for (String day : emp.getWeeklyOffDays().split(",")) {
                try {
                    weeklyOffDays.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
                } catch (Exception ignored) {}
            }
        }
        
        // If no employee-level, check org-level
        if (weeklyOffDays.isEmpty()) {
            WeeklyOffConfig weeklyOff = weeklyOffRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(
                    orgId, emp.getEmploymentType()).orElse(null);
            if (weeklyOff != null && weeklyOff.getWeeklyOffDays() != null) {
                for (String day : weeklyOff.getWeeklyOffDays().split(",")) {
                    try {
                        weeklyOffDays.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
                    } catch (Exception ignored) {}
                }
            }
        }
        
        // Default to Sunday if nothing configured
        if (weeklyOffDays.isEmpty()) {
            weeklyOffDays.add(DayOfWeek.SUNDAY);
        }
        
        return weeklyOffDays;
    }

    private String getEmployeeName(Employee emp) {
        String fn = emp.getFirstName() != null ? emp.getFirstName() : "";
        String ln = emp.getLastName() != null ? emp.getLastName() : "";
        String name = (fn + " " + ln).trim();
        return name.isEmpty() ? emp.getEmpCode() : name;
    }

    /**
     * Generate payroll for all active employees who have attendance in the month
     * Only employees with at least 1 present day are included
     */
    public List<Payroll> generateMonthlyPayroll(String orgId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        
        // Use TenantContext for proper tenant isolation
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = orgId;
        }
        
        // Filter employees by tenant and status
        final String effectiveTenantId = tenantId;
        List<Employee> employees = employeeRepo.findAll().stream()
                .filter(e -> e.getStatus() == com.example.hrms.domain.enums.EmployeeStatus.ACTIVE)
                .filter(e -> effectiveTenantId.equals(e.getTenantId()))
                .toList();

        // Get attendance for this TENANT and month only (efficient query)
        List<AttendanceDay> allAttendance = attendanceDayRepo.findByTenantIdAndWorkDateBetween(
                effectiveTenantId, startDate, endDate);
        
        // Group attendance by employee and check for present days
        Map<Long, Integer> employeePresentDays = new HashMap<>();
        for (AttendanceDay ad : allAttendance) {
            String status = ad.getStatus() != null ? ad.getStatus().toUpperCase() : "";
            // Count present, half day, OT_DAY as having attendance
            if ("PRESENT".equals(status) || "HALF_DAY".equals(status) || "PARTIAL".equals(status) || "OT_DAY".equals(status)) {
                employeePresentDays.merge(ad.getEmployeeId(), 1, Integer::sum);
            }
        }

        List<Payroll> payrolls = new ArrayList<>();
        List<String> skippedEmployees = new ArrayList<>();
        
        for (Employee emp : employees) {
            // Only generate payroll if employee has at least 1 present day
            Integer presentCount = employeePresentDays.get(emp.getId());
            if (presentCount == null || presentCount == 0) {
                skippedEmployees.add(emp.getEmpCode() + " (" + getEmployeeName(emp) + ")");
                continue; // Skip employees with no attendance
            }
            
            try {
                Payroll p = generatePayroll(orgId, emp.getEmpCode(), year, month);
                payrolls.add(p);
            } catch (Exception e) {
                System.err.println("Failed to generate payroll for " + emp.getEmpCode() + ": " + e.getMessage());
            }
        }
        
        // Log skipped employees for reference
        if (!skippedEmployees.isEmpty()) {
            System.out.println("Skipped " + skippedEmployees.size() + " employees with no attendance for " + 
                    ym.getMonth() + " " + year + ": " + skippedEmployees);
        }
        
        return payrolls;
    }

    /**
     * Get payroll for a specific month
     */
    public List<Payroll> getMonthlyPayroll(String orgId, int year, int month) {
        return payrollRepo.findByOrgIdAndYearAndMonthOrderByEmpIdAsc(orgId, year, month);
    }

    /**
     * Get employee payroll history
     */
    public List<Payroll> getEmployeePayrollHistory(String orgId, String empId) {
        return payrollRepo.findByOrgIdAndEmpIdOrderByYearDescMonthDesc(orgId, empId);
    }

    /**
     * Get payroll by ID
     */
    public Optional<Payroll> getPayroll(Long payrollId) {
        return payrollRepo.findById(payrollId);
    }

    /**
     * Update payroll (for manual adjustments)
     * Note: Advance field should contain ONLY manual advances from the UI.
     * Loan EMI is added automatically to ensure it's always deducted.
     */
    public Payroll updatePayroll(Long payrollId, Payroll updates) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        // Only allow updates if status is DRAFT
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        // Update allowed fields
        // For advance: UI sends manual advance, we add loan EMI to it
        if (updates.getAdvance() != null) {
            BigDecimal manualAdvance = updates.getAdvance();
            BigDecimal loanEmi = safeAdd(payroll.getLoanDeduction());
            payroll.setAdvance(loanEmi.add(manualAdvance)); // ADV = Loan EMI + Manual Advance
        }
        if (updates.getDue() != null) payroll.setDue(updates.getDue());
        if (updates.getBonus() != null) payroll.setBonus(updates.getBonus());
        if (updates.getIncentive() != null) payroll.setIncentive(updates.getIncentive());
        if (updates.getOtherDeduction() != null) payroll.setOtherDeduction(updates.getOtherDeduction());
        if (updates.getRemarks() != null) payroll.setRemarks(updates.getRemarks());

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }

    /**
     * Approve payroll
     */
    public Payroll approvePayroll(Long payrollId, String approvedBy) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));
        
        payroll.setStatus(PayrollStatus.APPROVED);
        payroll.setApprovedBy(approvedBy);
        return payrollRepo.save(payroll);
    }

    /**
     * Approve all payrolls for a month
     */
    public List<Payroll> approveMonthlyPayroll(String orgId, int year, int month, String approvedBy) {
        List<Payroll> payrolls = payrollRepo.findByOrgIdAndYearAndMonthAndStatus(orgId, year, month, PayrollStatus.DRAFT);
        for (Payroll p : payrolls) {
            p.setStatus(PayrollStatus.APPROVED);
            p.setApprovedBy(approvedBy);
        }
        return payrollRepo.saveAll(payrolls);
    }

    /**
     * Mark payroll as paid with payment details
     */
    public Payroll markAsPaid(Long payrollId, PaymentMode paymentMode, String transactionRef, 
                              String bankName, String bankAccount, String upiId, String chequeNumber, String paidBy) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.APPROVED) {
            throw new IllegalStateException("Payroll must be approved before marking as paid");
        }

        payroll.setStatus(PayrollStatus.PAID);
        payroll.setPaidDate(LocalDate.now());
        payroll.setPaymentTimestamp(LocalDateTime.now());
        payroll.setPaymentMode(paymentMode);
        payroll.setTransactionReference(transactionRef);
        payroll.setBankName(bankName);
        payroll.setBankAccount(bankAccount);
        payroll.setUpiId(upiId);
        payroll.setChequeNumber(chequeNumber);
        payroll.setPaidBy(paidBy);

        // Process loan EMI deductions
        if (payroll.getLoanDeduction() != null && payroll.getLoanDeduction().compareTo(BigDecimal.ZERO) > 0) {
            List<com.example.hrms.loan.domain.Loan> activeLoans = loanService.getActiveLoans(
                    payroll.getOrgId(), payroll.getEmpId());
            for (com.example.hrms.loan.domain.Loan loan : activeLoans) {
                loanService.processEmiPayment(loan.getId(), payroll.getId());
            }
        }

        return payrollRepo.save(payroll);
    }

    /**
     * Bulk payment processing
     */
    public List<Payroll> processMonthlyPayment(String orgId, int year, int month, 
                                                PaymentMode paymentMode, String paidBy) {
        List<Payroll> payrolls = payrollRepo.findByOrgIdAndYearAndMonthAndStatus(orgId, year, month, PayrollStatus.APPROVED);
        
        for (Payroll p : payrolls) {
            p.setStatus(PayrollStatus.PAID);
            p.setPaidDate(LocalDate.now());
            p.setPaymentTimestamp(LocalDateTime.now());
            p.setPaymentMode(paymentMode);
            p.setPaidBy(paidBy);

            // Process loan EMI
            if (p.getLoanDeduction() != null && p.getLoanDeduction().compareTo(BigDecimal.ZERO) > 0) {
                List<com.example.hrms.loan.domain.Loan> activeLoans = loanService.getActiveLoans(
                        p.getOrgId(), p.getEmpId());
                for (com.example.hrms.loan.domain.Loan loan : activeLoans) {
                    loanService.processEmiPayment(loan.getId(), p.getId());
                }
            }
        }
        
        return payrollRepo.saveAll(payrolls);
    }

    /**
     * Delete payroll (only if DRAFT)
     */
    public void deletePayroll(Long payrollId) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));
        
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot delete payroll with status: " + payroll.getStatus());
        }
        
        payrollRepo.delete(payroll);
    }

    /**
     * Delete all payrolls for a month (only if all are DRAFT)
     */
    public void deleteMonthlyPayroll(String orgId, int year, int month) {
        List<Payroll> payrolls = payrollRepo.findByOrgIdAndYearAndMonthOrderByEmpIdAsc(orgId, year, month);
        
        for (Payroll p : payrolls) {
            if (p.getStatus() != PayrollStatus.DRAFT) {
                throw new IllegalStateException("Cannot delete payroll with status: " + p.getStatus() + 
                        " for employee: " + p.getEmpId());
            }
        }
        
        payrollRepo.deleteAll(payrolls);
    }

    /**
     * Get employees who were skipped due to no attendance
     */
    public Map<String, Object> getSkippedEmployees(String orgId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        
        // Use TenantContext for proper tenant isolation
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = orgId;
        }
        
        // Filter employees by TENANT
        final String effectiveTenantId = tenantId;
        List<Employee> employees = employeeRepo.findAll().stream()
                .filter(e -> e.getStatus() == com.example.hrms.domain.enums.EmployeeStatus.ACTIVE)
                .filter(e -> effectiveTenantId.equals(e.getTenantId()))
                .toList();

        // Get attendance for this TENANT and month only
        List<AttendanceDay> allAttendance = attendanceDayRepo.findByTenantIdAndWorkDateBetween(
                effectiveTenantId, startDate, endDate);
        
        // Group by employee and check for present days
        Map<Long, Integer> employeePresentDays = new HashMap<>();
        for (AttendanceDay ad : allAttendance) {
            String status = ad.getStatus() != null ? ad.getStatus().toUpperCase() : "";
            if ("PRESENT".equals(status) || "HALF_DAY".equals(status) || "PARTIAL".equals(status) || "OT_DAY".equals(status)) {
                employeePresentDays.merge(ad.getEmployeeId(), 1, Integer::sum);
            }
        }
        
        // Find employees with no attendance
        List<Map<String, Object>> skippedEmployees = new ArrayList<>();
        for (Employee emp : employees) {
            Integer presentCount = employeePresentDays.get(emp.getId());
            if (presentCount == null || presentCount == 0) {
                Map<String, Object> empInfo = new LinkedHashMap<>();
                empInfo.put("empCode", emp.getEmpCode());
                empInfo.put("empName", getEmployeeName(emp));
                empInfo.put("employmentType", emp.getEmploymentType());
                skippedEmployees.add(empInfo);
            }
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("monthName", ym.getMonth().toString());
        result.put("skippedCount", skippedEmployees.size());
        result.put("skippedEmployees", skippedEmployees);
        result.put("message", skippedEmployees.isEmpty() ? 
                "All employees have attendance for this month" : 
                skippedEmployees.size() + " employee(s) had no attendance and were not included in payroll");
        
        return result;
    }

    /**
     * Get payroll summary for a month
     */
    public Map<String, Object> getPayrollSummary(String orgId, int year, int month) {
        List<Payroll> payrolls = getMonthlyPayroll(orgId, year, month);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalEsi = BigDecimal.ZERO;
        BigDecimal totalPfEmployee = BigDecimal.ZERO;
        BigDecimal totalPfCompany = BigDecimal.ZERO;
        BigDecimal totalAdvance = BigDecimal.ZERO;
        BigDecimal totalLoan = BigDecimal.ZERO;

        int draftCount = 0, approvedCount = 0, paidCount = 0;

        for (Payroll p : payrolls) {
            totalGross = totalGross.add(safeAdd(p.getGrossSalary()));
            totalDeductions = totalDeductions.add(safeAdd(p.getTotalDeductions()));
            totalNet = totalNet.add(safeAdd(p.getNetSalary()));
            totalEsi = totalEsi.add(safeAdd(p.getEsiEmployee()));
            totalPfEmployee = totalPfEmployee.add(safeAdd(p.getPfEmployee()));
            totalPfCompany = totalPfCompany.add(safeAdd(p.getPfCompany()));
            totalAdvance = totalAdvance.add(safeAdd(p.getAdvance()));
            totalLoan = totalLoan.add(safeAdd(p.getLoanDeduction()));

            switch (p.getStatus()) {
                case DRAFT -> draftCount++;
                case APPROVED -> approvedCount++;
                case PAID -> paidCount++;
                default -> {}
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("year", year);
        summary.put("month", month);
        summary.put("employeeCount", payrolls.size());
        summary.put("draftCount", draftCount);
        summary.put("approvedCount", approvedCount);
        summary.put("paidCount", paidCount);
        summary.put("totalGrossSalary", totalGross);
        summary.put("totalDeductions", totalDeductions);
        summary.put("totalNetSalary", totalNet);
        summary.put("totalEsi", totalEsi);
        summary.put("totalPfEmployee", totalPfEmployee);
        summary.put("totalPfCompany", totalPfCompany);
        summary.put("totalAdvance", totalAdvance);
        summary.put("totalLoanDeduction", totalLoan);

        return summary;
    }

    /**
     * Check if attendance data is available for payroll generation
     */
    public Map<String, Object> checkAttendanceAvailability(String orgId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        
        // Use TenantContext for proper tenant isolation
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = orgId;
        }

        // Get employees for THIS TENANT ONLY
        final String effectiveTenantId = tenantId;
        List<Employee> employees = employeeRepo.findAll().stream()
                .filter(e -> e.getStatus() == com.example.hrms.domain.enums.EmployeeStatus.ACTIVE)
                .filter(e -> effectiveTenantId.equals(e.getTenantId()))
                .toList();

        // Check if attendance records exist FOR THIS TENANT AND MONTH
        List<AttendanceDay> attendanceRecords = attendanceDayRepo.findByTenantIdAndWorkDateBetween(
                effectiveTenantId, start, end);

        int employeesWithAttendance = (int) attendanceRecords.stream()
                .map(AttendanceDay::getEmployeeId)
                .distinct()
                .count();

        boolean isAvailable = !attendanceRecords.isEmpty() && employeesWithAttendance > 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", isAvailable);
        result.put("year", year);
        result.put("month", month);
        result.put("totalEmployees", employees.size());
        result.put("employeesWithAttendance", employeesWithAttendance);
        result.put("attendanceRecords", attendanceRecords.size());
        
        if (!isAvailable) {
            result.put("message", "No attendance data for " + ym.getMonth() + " " + year + 
                    ". Please import attendance before generating payroll.");
        }

        return result;
    }

    /**
     * Check if attendance data is available for a specific employee
     */
    public Map<String, Object> checkEmployeeAttendance(String orgId, String empId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // Use tenant-aware employee lookup
        String tenantId = TenantContext.getTenantId();
        Employee emp = (tenantId != null 
                ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empId)
                : employeeRepo.findByEmpCode(empId))
            .orElse(null);
        if (emp == null) {
            return Map.of("available", false, "message", "Employee not found: " + empId);
        }

        List<AttendanceDay> records = attendanceDayRepo.findByEmployeeIdAndWorkDateBetween(
                emp.getId(), start, end);

        boolean isAvailable = !records.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", isAvailable);
        result.put("empId", empId);
        result.put("empName", getEmployeeName(emp));
        result.put("year", year);
        result.put("month", month);
        result.put("attendanceRecords", records.size());

        if (!isAvailable) {
            result.put("message", "Attendance not found for " + empId + " in " + ym.getMonth() + " " + year);
        }

        return result;
    }

    /**
     * Get detailed payroll with loan, leave, and advance info for verification
     */
    public Map<String, Object> getPayrollDetails(Long payrollId) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        Map<String, Object> details = new LinkedHashMap<>();
        
        // Basic payroll info
        details.put("id", payroll.getId());
        details.put("empId", payroll.getEmpId());
        details.put("empName", payroll.getEmpName());
        details.put("year", payroll.getYear());
        details.put("month", payroll.getMonth());
        details.put("status", payroll.getStatus().name());

        // Salary structure
        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("basicSalary", payroll.getBasicSalary());
        salary.put("increment", payroll.getIncrement());
        salary.put("finalPayment", payroll.getFinalPayment());
        details.put("salaryStructure", salary);

        // Attendance summary
        Map<String, Object> attendance = new LinkedHashMap<>();
        attendance.put("totalWorkingDays", payroll.getTotalWorkingDays());
        attendance.put("presentDays", payroll.getPresentDays());
        attendance.put("absentDays", payroll.getAbsentDays());
        attendance.put("halfDays", payroll.getHalfDays());
        attendance.put("lateDays", payroll.getLateDays());
        attendance.put("weeklyOffDays", payroll.getWeeklyOffDays());
        attendance.put("holidayDays", payroll.getHolidayDays());
        attendance.put("overtimeDays", payroll.getOvertimeDays());
        attendance.put("overtimeHours", payroll.getOvertimeHours());
        details.put("attendance", attendance);

        // Earnings
        Map<String, Object> earnings = new LinkedHashMap<>();
        earnings.put("workingDayAmount", payroll.getWorkingDayAmount());
        earnings.put("overtimeDayAmount", payroll.getOvertimeDayAmount());
        earnings.put("overtimeHourAmount", payroll.getOvertimeHourAmount());
        earnings.put("houseRent", payroll.getHouseRent());
        earnings.put("medicalExpense", payroll.getMedicalExpense());
        earnings.put("bonus", payroll.getBonus());
        earnings.put("incentive", payroll.getIncentive());
        earnings.put("grossSalary", payroll.getGrossSalary());
        details.put("earnings", earnings);

        // Deductions breakdown
        Map<String, Object> deductions = new LinkedHashMap<>();
        deductions.put("esiEmployee", payroll.getEsiEmployee());
        deductions.put("pfEmployee", payroll.getPfEmployee());
        deductions.put("pfCompany", payroll.getPfCompany());
        deductions.put("professionalTax", payroll.getProfessionalTax());
        deductions.put("tds", payroll.getTds());
        details.put("statutoryDeductions", deductions);

        // Loan info
        Map<String, Object> loanInfo = new LinkedHashMap<>();
        BigDecimal loanEmi = loanService.getMonthlyEmiDeduction(payroll.getOrgId(), payroll.getEmpId());
        List<com.example.hrms.loan.domain.Loan> activeLoans = loanService.getActiveLoans(
                payroll.getOrgId(), payroll.getEmpId());
        
        loanInfo.put("monthlyEmi", loanEmi);
        loanInfo.put("activeLoansCount", activeLoans.size());
        loanInfo.put("activeLoans", activeLoans.stream().map(l -> Map.of(
                "loanId", l.getId(),
                "loanType", l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN",
                "principalAmount", l.getPrincipalAmount(),
                "emiAmount", l.getEmiAmount(),
                "outstandingBalance", l.getOutstandingBalance(),
                "emisPaid", l.getEmisPaid(),
                "tenureMonths", l.getTenureMonths()
        )).toList());
        details.put("loanInfo", loanInfo);

        // Advance and Due
        Map<String, Object> advanceDue = new LinkedHashMap<>();
        advanceDue.put("advance", payroll.getAdvance());
        advanceDue.put("loanEmiInAdvance", payroll.getLoanDeduction());
        advanceDue.put("manualAdvance", safeAdd(payroll.getAdvance()).subtract(safeAdd(payroll.getLoanDeduction())));
        advanceDue.put("due", payroll.getDue());
        advanceDue.put("otherDeduction", payroll.getOtherDeduction());
        details.put("advanceAndDue", advanceDue);

        // Net calculation
        Map<String, Object> netCalc = new LinkedHashMap<>();
        netCalc.put("grossSalary", payroll.getGrossSalary());
        netCalc.put("totalDeductions", payroll.getTotalDeductions());
        netCalc.put("netSalary", payroll.getNetSalary());
        details.put("netCalculation", netCalc);

        // Payment info (if paid)
        if (payroll.getStatus() == PayrollStatus.PAID) {
            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("paidDate", payroll.getPaidDate());
            payment.put("paymentMode", payroll.getPaymentMode());
            payment.put("transactionReference", payroll.getTransactionReference());
            payment.put("paidBy", payroll.getPaidBy());
            details.put("paymentInfo", payment);
        }

        details.put("remarks", payroll.getRemarks());

        return details;
    }

    /**
     * Get detailed monthly payroll for all employees (for verification)
     */
    public List<Map<String, Object>> getDetailedMonthlyPayroll(String orgId, int year, int month) {
        List<Payroll> payrolls = getMonthlyPayroll(orgId, year, month);
        
        return payrolls.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("empId", p.getEmpId());
            row.put("empName", p.getEmpName());
            row.put("basicSalary", p.getBasicSalary());
            row.put("increment", p.getIncrement());
            row.put("finalPayment", p.getFinalPayment());
            row.put("totalWorkingDays", p.getTotalWorkingDays());
            row.put("presentDays", p.getPresentDays());
            row.put("absentDays", p.getAbsentDays());
            row.put("workingDayAmount", p.getWorkingDayAmount());
            row.put("overtimeDays", p.getOvertimeDays());
            row.put("overtimeHours", p.getOvertimeHours());
            row.put("overtimeDayAmount", p.getOvertimeDayAmount());
            row.put("overtimeHourAmount", p.getOvertimeHourAmount());
            row.put("houseRent", p.getHouseRent());
            row.put("medicalExpense", p.getMedicalExpense());
            row.put("grossSalary", p.getGrossSalary());
            row.put("esiEmployee", p.getEsiEmployee());
            row.put("pfEmployee", p.getPfEmployee());
            row.put("pfCompany", p.getPfCompany());
            
            // Get loan EMI
            BigDecimal loanEmi = loanService.getMonthlyEmiDeduction(orgId, p.getEmpId());
            row.put("loanEmi", loanEmi);
            
            // Calculate manual advance (advance - loan EMI)
            BigDecimal manualAdvance = safeAdd(p.getAdvance()).subtract(safeAdd(p.getLoanDeduction()));
            if (manualAdvance.compareTo(BigDecimal.ZERO) < 0) manualAdvance = BigDecimal.ZERO;
            row.put("manualAdvance", manualAdvance);
            
            row.put("advance", p.getAdvance());
            row.put("due", p.getDue());
            row.put("totalDeductions", p.getTotalDeductions());
            row.put("netSalary", p.getNetSalary());
            row.put("status", p.getStatus().name());
            row.put("remarks", p.getRemarks());
            
            return row;
        }).toList();
    }

    /**
     * Get only PAID payrolls for salary sheet
     */
    public List<Payroll> getPaidPayroll(String orgId, int year, int month) {
        return payrollRepo.findByOrgIdAndYearAndMonthAndStatus(orgId, year, month, PayrollStatus.PAID);
    }

    /**
     * Update manual advance for an employee
     * Manual advance is tracked separately from loan EMI
     */
    public Payroll updateManualAdvance(Long payrollId, BigDecimal manualAdvance, String remarks) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        // Calculate new advance = loan EMI + manual advance
        BigDecimal loanEmi = safeAdd(payroll.getLoanDeduction());
        payroll.setAdvance(loanEmi.add(manualAdvance));
        
        // Update remarks
        String existingRemarks = payroll.getRemarks() != null ? payroll.getRemarks() : "";
        String newRemarks = existingRemarks.isEmpty() ? remarks : existingRemarks + "; " + remarks;
        payroll.setRemarks(newRemarks);

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }

    /**
     * Update due amount for an employee
     */
    public Payroll updateDue(Long payrollId, BigDecimal due, String remarks) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        payroll.setDue(due);
        
        // Update remarks
        String existingRemarks = payroll.getRemarks() != null ? payroll.getRemarks() : "";
        String newRemarks = existingRemarks.isEmpty() ? remarks : existingRemarks + "; " + remarks;
        payroll.setRemarks(newRemarks);

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }
}
