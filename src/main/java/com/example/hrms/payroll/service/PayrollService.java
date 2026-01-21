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
import lombok.extern.slf4j.Slf4j;
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
 * Payroll Service implementing the exact payment sheet calculation from Excel:
 * 
 * WORKING DAY AMOUNT = (FINAL PAYMENT × PRESENT DAYS) / THRESHOLD DAYS (e.g., 28)
 * OT DAY AMOUNT = (FINAL PAYMENT / WORKING DAYS) * OT DAYS
 * OT HR AMOUNT = (FINAL PAYMENT / WORKING DAYS / 8) * OT HOURS
 * GROSS SALARY = WORKING DAY AMOUNT + OT DAY AMOUNT + OT HR AMOUNT + HOUSE RENT + MEDICAL + BONUS + INCENTIVE
 * 
 * Deductions (calculated on Working Day Amount, not Final Payment):
 * ESI = WORKING DAY AMOUNT * 0.75%
 * PF OWN = WORKING DAY AMOUNT * 6%
 * PF COMPANY = WORKING DAY AMOUNT * 6%
 * 
 * NET SALARY = GROSS - ESI - PF_Own - PF_Company - ADV + DUE
 * (DUE is ADDED because it's money owed TO employee)
 */
@Service
@Transactional
@Slf4j
public class PayrollService {

    private final PayrollRepository payrollRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceDayRepository attendanceDayRepo;
    private final HolidayRepository holidayRepo;
    private final WeeklyOffConfigRepository weeklyOffRepo;
    private final LoanService loanService;
    private final SalaryOvertimeConfigService configService;

    // Default deduction rates (fallback if config not set) - these are now org-configurable
    private static final BigDecimal DEFAULT_ESI_RATE = new BigDecimal("0.0075");  // 0.75%
    private static final BigDecimal DEFAULT_PF_RATE = new BigDecimal("0.06");     // 6%

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

        Payroll savedPayroll = payrollRepo.save(payroll);
        
        // Step 6: Mark one-time loans as deducted in this payroll
        // This prevents them from being deducted again if payroll is regenerated
        loanService.markOneTimeLoansAsDeducted(
                savedPayroll.getOrgId(), 
                savedPayroll.getEmpId(), 
                savedPayroll.getId(), 
                month, 
                year);
        
        return savedPayroll;
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
        int paidLeaveDays = 0;  // Count paid leave days
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;
        
        // Check if employee is eligible for OT
        boolean employeeOtAllowed = emp.isOtAllowed();
        SalaryOvertimeConfig otConfig = configService.getConfig();
        boolean orgOtEnabled = otConfig.getOvertimeEnabled() != null && otConfig.getOvertimeEnabled();
        boolean canCountOt = employeeOtAllowed && orgOtEnabled;

        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            boolean isWeeklyOff = weeklyOffDays.contains(date.getDayOfWeek());
            boolean isHoliday = holidayDates.contains(date);

            if (isWeeklyOff) {
                weeklyOffCount++;
                // Check for OT on weekly off (only if employee is OT allowed)
                if (canCountOt) {
                    AttendanceDay ad = attendanceMap.get(date);
                    if (ad != null && ad.getTotalWorkMin() != null && ad.getTotalWorkMin() > 0) {
                        overtimeDays++;
                        BigDecimal hours = BigDecimal.valueOf(ad.getTotalWorkMin())
                                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                        totalOvertimeHours = totalOvertimeHours.add(hours);
                    }
                }
            } else if (isHoliday) {
                holidayCount++;
                // Check for OT on holiday (only if employee is OT allowed)
                if (canCountOt) {
                    AttendanceDay ad = attendanceMap.get(date);
                    if (ad != null && ad.getTotalWorkMin() != null && ad.getTotalWorkMin() > 0) {
                        overtimeDays++;
                        BigDecimal hours = BigDecimal.valueOf(ad.getTotalWorkMin())
                                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                        totalOvertimeHours = totalOvertimeHours.add(hours);
                    }
                }
            } else {
                totalWorkingDays++;

                AttendanceDay ad = attendanceMap.get(date);
                if (ad != null) {
                    String status = ad.getStatus() != null ? ad.getStatus().toUpperCase() : "";
                    
                    if ("PRESENT".equals(status)) {
                        presentDays++;
                        
                        // Check for late - skip if already approved by admin
                        if (ad.getLateByMins() != null && ad.getLateByMins() > 0 
                                && !Boolean.TRUE.equals(ad.getLateApproved())) {
                            lateDays++;
                        }
                        
                        // Check for OT on working day (only if employee is OT allowed)
                        if (canCountOt && ad.getTotalWorkMin() != null) {
                            int standardMins = otConfig.getStandardWorkingHoursPerDay() * 60;
                            int otMinThreshold = otConfig.getOvertimeMinThresholdMins();
                            int extraMins = ad.getTotalWorkMin() - standardMins;
                            
                            // Only count as OT if extra minutes exceed the minimum threshold
                            // e.g., if threshold is 30 mins, working 29 mins extra = no OT
                            if (extraMins >= otMinThreshold) {
                                BigDecimal otMins = BigDecimal.valueOf(extraMins);
                                totalOvertimeHours = totalOvertimeHours.add(
                                        otMins.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                            }
                        }
                    } else if ("PARTIAL".equals(status) || "HALF_DAY".equals(status)) {
                        halfDays++;
                    } else if ("LEAVE".equals(status)) {
                        // Paid leave - counts as a working day for salary calculation
                        paidLeaveDays++;
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
        payroll.setPaidLeaveDays(paidLeaveDays);
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
        int presentDays = payroll.getPresentDays() != null ? payroll.getPresentDays() : 0;
        int halfDays = payroll.getHalfDays() != null ? payroll.getHalfDays() : 0;
        int paidLeaveDays = payroll.getPaidLeaveDays() != null ? payroll.getPaidLeaveDays() : 0;
        
        // Get configured values
        int salaryDaysInMonth = config.getSalaryCalculationDaysInMonth(); // e.g., 30 (for per-day rate)
        int fullMonthThreshold = config.getFullMonthSalaryThresholdDays(); // e.g., 28 (for salary calculation)
        int standardHoursPerDay = config.getStandardWorkingHoursPerDay(); // e.g., 8
        boolean thresholdEnabled = config.getEnableFullMonthSalaryThreshold();

        // Total effective present days = present + half days + paid leave days
        // This matches Excel logic where HALF_DAY and LEAVE are counted as full present days for salary
        int effectivePresentDays = presentDays + halfDays + paidLeaveDays;
        
        // WORKING DAY AMOUNT calculation based on Excel formula:
        // If employee worked >= threshold days, they get FULL salary
        // Otherwise: Working Day Amount = Final Payment * Present Days / Threshold Days
        BigDecimal workingDayAmount;
        if (thresholdEnabled && effectivePresentDays >= fullMonthThreshold) {
            // Employee qualifies for full month salary
            workingDayAmount = finalPayment;
            log.debug("Employee {} qualified for full salary: {} >= {} threshold days", 
                    payroll.getEmpName(), effectivePresentDays, fullMonthThreshold);
        } else {
            // Pay based on actual days worked
            // Formula: Final Payment * Effective Present Days / Threshold Days
            workingDayAmount = finalPayment
                    .multiply(BigDecimal.valueOf(effectivePresentDays))
                    .divide(BigDecimal.valueOf(fullMonthThreshold), 0, RoundingMode.HALF_UP);
            log.debug("Employee {} partial salary: {} * {} / {} = {}", 
                    payroll.getEmpName(), finalPayment, effectivePresentDays, fullMonthThreshold, workingDayAmount);
        }
        payroll.setWorkingDayAmount(workingDayAmount);
        
        // Per day rate = FINAL PAYMENT / THRESHOLD DAYS (for OT calculation)
        BigDecimal perDayRate = finalPayment.divide(BigDecimal.valueOf(fullMonthThreshold), 4, RoundingMode.HALF_UP);
        
        // Per hour rate = Per day rate / STANDARD HOURS (for OT hours calculation)
        BigDecimal perHourRate = perDayRate.divide(BigDecimal.valueOf(standardHoursPerDay), 4, RoundingMode.HALF_UP);

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
     * Calculate deductions based on organization-wide configuration
     * ESI, PF rates are from SalaryOvertimeConfig (org-level)
     * Applicability flags are from Employee (employee-level)
     */
    private void calculateDeductions(Payroll payroll, Employee emp) {
        // Get organization config for statutory rates
        SalaryOvertimeConfig config = configService.getConfig();
        
        // Get rates from org config (with fallback to defaults)
        BigDecimal esiEmployeeRate = config.getEsiEmployeeRate() != null 
                ? config.getEsiEmployeeRate() : DEFAULT_ESI_RATE;
        BigDecimal esiEmployerRate = config.getEsiEmployerRate() != null 
                ? config.getEsiEmployerRate() : new BigDecimal("0.0325");
        BigDecimal pfEmployeeRate = config.getPfEmployeeRate() != null 
                ? config.getPfEmployeeRate() : DEFAULT_PF_RATE;
        BigDecimal pfEmployerRate = config.getPfEmployerRate() != null 
                ? config.getPfEmployerRate() : DEFAULT_PF_RATE;
        BigDecimal esiWageCeiling = config.getEsiWageCeiling() != null 
                ? config.getEsiWageCeiling() : new BigDecimal("21000");
        
        // First calculate gross to determine ESI
        BigDecimal grossSalary = safeAdd(payroll.getWorkingDayAmount())
                .add(safeAdd(payroll.getOvertimeDayAmount()))
                .add(safeAdd(payroll.getOvertimeHourAmount()))
                .add(safeAdd(payroll.getHouseRent()))
                .add(safeAdd(payroll.getMedicalExpense()))
                .add(safeAdd(payroll.getConveyanceAllowance()))
                .add(safeAdd(payroll.getSpecialAllowance()))
                .add(safeAdd(payroll.getOtherAllowance()));

        // ESI = WORKING DAY AMOUNT * esi_employee_rate (org-configurable)
        // Calculated on prorated salary (Working Day Amount), matching Excel calculation
        // Only applies if employee is ESI applicable AND working day amount <= ESI wage ceiling
        boolean esicApplicable = emp.getEsicApplicable() == null || Boolean.TRUE.equals(emp.getEsicApplicable());
        BigDecimal workingDayAmount = safeAdd(payroll.getWorkingDayAmount());
        boolean withinEsiCeiling = workingDayAmount.compareTo(esiWageCeiling) <= 0;
        if (esicApplicable && withinEsiCeiling) {
            // ESI calculated on Working Day Amount (prorated salary)
            BigDecimal esiEmployee = workingDayAmount.multiply(esiEmployeeRate).setScale(0, RoundingMode.HALF_UP);
            BigDecimal esiEmployer = workingDayAmount.multiply(esiEmployerRate).setScale(0, RoundingMode.HALF_UP);
            payroll.setEsiEmployee(esiEmployee);
            // Store employer contribution if needed for reports
        }

        // PF = WORKING DAY AMOUNT * pf_employee_rate (org-configurable)
        // Based on prorated salary (Working Day Amount), matching Excel calculation
        // If employee worked full month (present >= threshold), PF is on full salary
        boolean epfApplicable = emp.getEpfApplicable() == null || Boolean.TRUE.equals(emp.getEpfApplicable());
        if (epfApplicable) {
            // Use Working Day Amount (prorated salary) as base for PF
            BigDecimal pfBase = safeAdd(payroll.getWorkingDayAmount());
            BigDecimal pfOwn = pfBase.multiply(pfEmployeeRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal pfCompany = pfBase.multiply(pfEmployerRate).setScale(4, RoundingMode.HALF_UP);
            payroll.setPfEmployee(pfOwn);
            payroll.setPfCompany(pfCompany);
        }

        // Loan deduction from loan module (tenant-aware)
        String tenantId = payroll.getTenantId() != null ? payroll.getTenantId() : payroll.getOrgId();
        BigDecimal loanEmi = loanService.getMonthlyEmiDeduction(tenantId, payroll.getEmpId());
        payroll.setLoanDeduction(loanEmi); // Keep for detailed reporting

        // Auto-populate Due from overdue loan EMIs (as of payroll month start)
        // For July 2025 payroll, this checks for EMIs overdue before July 1, 2025
        LocalDate payrollMonthStart = LocalDate.of(payroll.getYear(), payroll.getMonth(), 1);
        Map<String, Object> overdueInfo = getOutstandingLoanDues(tenantId, payroll.getEmpId(), payrollMonthStart);
        BigDecimal overdueLoanAmount = (BigDecimal) overdueInfo.get("totalOverdue");
        if (overdueLoanAmount != null && overdueLoanAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Add overdue EMIs to Due column
            BigDecimal existingDue = safeAdd(payroll.getDue());
            payroll.setDue(existingDue.add(overdueLoanAmount));
        }

        // Professional Tax (if applicable) - use configured amount from Salary & OT Config
        BigDecimal configuredPtAmount = config.getProfessionalTaxAmount();
        if (Boolean.TRUE.equals(emp.getPtApplicable()) && 
            configuredPtAmount != null && configuredPtAmount.compareTo(BigDecimal.ZERO) > 0 &&
            grossSalary.compareTo(new BigDecimal("10000")) > 0) {
            payroll.setProfessionalTax(configuredPtAmount);
        } else {
            payroll.setProfessionalTax(BigDecimal.ZERO);
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
        
        // NOTE: No default weekly off - consistent with attendance engine
        // If organization wants Sunday as weekly off, they should configure it in weekly off settings
        // This ensures payroll and attendance calculations match
        
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
     * Also clears one-time loan associations so they can be re-deducted
     */
    public void deletePayroll(Long payrollId) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));
        
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot delete payroll with status: " + payroll.getStatus());
        }
        
        // Clear one-time loan associations before deleting payroll
        loanService.clearOneTimeLoanAssociations(payrollId);
        
        payrollRepo.delete(payroll);
    }

    /**
     * Delete all payrolls for a month (only if all are DRAFT)
     * Also clears one-time loan associations so they can be re-deducted
     * @return number of payrolls deleted
     */
    @Transactional
    public int deleteMonthlyPayroll(String orgId, int year, int month) {
        log.info("🗑️ Deleting payrolls for orgId={}, year={}, month={}", orgId, year, month);
        
        List<Payroll> payrolls = payrollRepo.findByOrgIdAndYearAndMonthOrderByEmpIdAsc(orgId, year, month);
        log.info("📋 Found {} payroll records", payrolls.size());
        
        if (payrolls.isEmpty()) {
            log.info("ℹ️ No payrolls found to delete");
            return 0;
        }
        
        // Check if any are not in DRAFT status
        for (Payroll p : payrolls) {
            if (p.getStatus() != PayrollStatus.DRAFT) {
                throw new IllegalStateException("Cannot delete payroll with status: " + p.getStatus() + 
                        " for employee: " + p.getEmpId());
            }
        }
        
        // Clear one-time loan associations before deleting payrolls
        // This makes the loans available for re-deduction in future payrolls
        log.info("🔄 Clearing one-time loan associations for month {}/{}", month, year);
        loanService.clearOneTimeLoanAssociationsForMonth(orgId, month, year);
        
        payrollRepo.deleteAll(payrolls);
        log.info("✅ Deleted {} payroll records", payrolls.size());
        return payrolls.size();
    }

    /**
     * Delete payroll for a single employee (for recalculation)
     * Also clears one-time loan associations so they can be re-deducted
     */
    @Transactional
    public void deleteEmployeePayroll(String orgId, String empId, int year, int month) {
        log.info("🗑️ Deleting payroll for employee={}, orgId={}, year={}, month={}", empId, orgId, year, month);
        
        Optional<Payroll> payrollOpt = payrollRepo.findByOrgIdAndEmpIdAndYearAndMonth(orgId, empId, year, month);
        
        if (payrollOpt.isEmpty()) {
            log.info("ℹ️ No payroll found for employee {}", empId);
            return;
        }
        
        Payroll payroll = payrollOpt.get();
        
        // Only allow deletion if status is DRAFT
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot delete payroll with status: " + payroll.getStatus());
        }
        
        // Clear one-time loan associations before deleting payroll
        loanService.clearOneTimeLoanAssociations(payroll.getId());
        
        payrollRepo.delete(payroll);
        log.info("✅ Deleted payroll for employee {}", empId);
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
     * Creates a FLEXIBLE LOAN (SALARY_ADVANCE) to track the advance
     * The advance is deducted from this month's payroll and tracked as a loan entry
     * This creates an audit trail in the loan system
     */
    public Payroll updateManualAdvance(Long payrollId, BigDecimal manualAdvance, String remarks) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        // If manual advance > 0, create a FLEXIBLE LOAN to track it
        // This creates an audit trail and tracks the advance in the loan system
        if (manualAdvance != null && manualAdvance.compareTo(BigDecimal.ZERO) > 0) {
            try {
                com.example.hrms.loan.domain.Loan advanceLoan = new com.example.hrms.loan.domain.Loan();
                advanceLoan.setTenantId(payroll.getTenantId());
                advanceLoan.setEmpId(payroll.getEmpId());
                advanceLoan.setLoanType(com.example.hrms.loan.domain.enums.LoanType.SALARY_ADVANCE);
                advanceLoan.setPrincipalAmount(manualAdvance);
                advanceLoan.setInterestRate(BigDecimal.ZERO);
                advanceLoan.setTenureMonths(0); // Flexible - no fixed tenure
                advanceLoan.setEmiAmount(BigDecimal.ZERO); // No fixed EMI
                advanceLoan.setSanctionDate(java.time.LocalDate.now());
                advanceLoan.setFirstEmiDate(null); // No fixed EMI date
                advanceLoan.setTotalRepayable(manualAdvance);
                // Since we're deducting now, mark as paid immediately
                advanceLoan.setOutstandingBalance(BigDecimal.ZERO);
                advanceLoan.setTotalPaid(manualAdvance);
                advanceLoan.setEmisPaid(1);
                advanceLoan.setIsOneTimeDeduction(false);
                advanceLoan.setIsFlexibleDeduction(true); // Mark as flexible loan
                advanceLoan.setStatus(com.example.hrms.loan.domain.enums.LoanStatus.CLOSED);
                advanceLoan.setClosedDate(java.time.LocalDate.now());
                advanceLoan.setRemarks("Advance given & deducted for " + payroll.getMonth() + "/" + payroll.getYear() + ": " + remarks);
                
                // Save the loan (creates audit trail)
                loanService.createLoan(advanceLoan);
                
                // Add to flexible loan deduction (this will be deducted from salary)
                BigDecimal currentFlexDeduction = safeAdd(payroll.getFlexibleLoanDeduction());
                payroll.setFlexibleLoanDeduction(currentFlexDeduction.add(manualAdvance));
            } catch (Exception e) {
                // If loan creation fails, still add to flexible deduction directly
                System.err.println("Failed to create advance loan: " + e.getMessage());
                BigDecimal currentFlexDeduction = safeAdd(payroll.getFlexibleLoanDeduction());
                payroll.setFlexibleLoanDeduction(currentFlexDeduction.add(manualAdvance));
            }
        }

        // Calculate new advance = fixed EMI + flexible loan deduction
        BigDecimal fixedEmi = safeAdd(payroll.getLoanDeduction());
        BigDecimal flexDeduction = safeAdd(payroll.getFlexibleLoanDeduction());
        payroll.setAdvance(fixedEmi.add(flexDeduction));
        
        // Update remarks
        String existingRemarks = payroll.getRemarks() != null ? payroll.getRemarks() : "";
        String advanceRemark = manualAdvance != null && manualAdvance.compareTo(BigDecimal.ZERO) > 0 
                ? "Advance: ₹" + manualAdvance + " (tracked in loan system)" 
                : "";
        if (!advanceRemark.isEmpty()) {
            payroll.setRemarks(existingRemarks.isEmpty() ? advanceRemark : existingRemarks + "; " + advanceRemark);
        }

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }

    /**
     * Update flexible loan deduction for an employee
     * Admin can choose how much to deduct from flexible loans this month
     * Also updates the loan's outstanding balance
     */
    public Payroll updateFlexibleLoanDeduction(Long payrollId, BigDecimal amount, Long loanId, String remarks) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deduction amount must be non-negative");
        }

        // Set the flexible loan deduction
        payroll.setFlexibleLoanDeduction(amount);
        
        // If a specific loan ID is provided, record the partial payment against it
        if (loanId != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                com.example.hrms.loan.domain.Loan loan = loanService.getLoan(loanId)
                        .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
                
                // Validate loan belongs to same employee and tenant
                if (!loan.getEmpId().equals(payroll.getEmpId()) || 
                    !loan.getTenantId().equals(payroll.getTenantId())) {
                    throw new IllegalArgumentException("Loan does not belong to this employee/tenant");
                }
                
                // Validate amount doesn't exceed outstanding balance
                if (amount.compareTo(loan.getOutstandingBalance()) > 0) {
                    amount = loan.getOutstandingBalance(); // Cap at outstanding
                    payroll.setFlexibleLoanDeduction(amount);
                }
                
                // Update loan outstanding balance (will be processed on payroll payment)
                // Note: Actual deduction from loan happens when payroll is paid
            } catch (Exception e) {
                System.err.println("Error processing flexible loan: " + e.getMessage());
            }
        }
        
        // Update advance to include flexible loan deduction
        BigDecimal fixedEmi = safeAdd(payroll.getLoanDeduction());
        BigDecimal flexDeduction = safeAdd(payroll.getFlexibleLoanDeduction());
        payroll.setAdvance(fixedEmi.add(flexDeduction));
        
        // Update remarks
        String existingRemarks = payroll.getRemarks() != null ? payroll.getRemarks() : "";
        String flexRemark = amount.compareTo(BigDecimal.ZERO) > 0 
                ? "Flexible loan deduction: ₹" + amount 
                : "";
        if (!flexRemark.isEmpty()) {
            payroll.setRemarks(existingRemarks.isEmpty() ? flexRemark : existingRemarks + "; " + flexRemark);
        }

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }

    /**
     * Update due amount for an employee
     * Due represents previous outstanding amounts that need to be deducted
     * If due is null or 0, auto-populate from overdue loan EMIs
     */
    public Payroll updateDue(Long payrollId, BigDecimal due, String remarks) {
        Payroll payroll = payrollRepo.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll not found: " + payrollId));

        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Cannot update payroll with status: " + payroll.getStatus());
        }

        // If due is not provided, check for overdue loan EMIs
        if (due == null || due.compareTo(BigDecimal.ZERO) == 0) {
            String tenantId = payroll.getTenantId() != null ? payroll.getTenantId() : payroll.getOrgId();
            Map<String, Object> overdueInfo = getOutstandingLoanDues(tenantId, payroll.getEmpId());
            BigDecimal overdueLoanAmount = (BigDecimal) overdueInfo.get("totalOverdue");
            if (overdueLoanAmount != null && overdueLoanAmount.compareTo(BigDecimal.ZERO) > 0) {
                due = overdueLoanAmount;
                remarks = "Auto-populated from " + overdueInfo.get("overdueCount") + " overdue loan EMI(s)";
            }
        }

        payroll.setDue(due);
        
        // Update remarks with due info
        String existingRemarks = payroll.getRemarks() != null ? payroll.getRemarks() : "";
        String dueRemark = due != null && due.compareTo(BigDecimal.ZERO) > 0 
                ? "Due: ₹" + due + " (previous outstanding)" 
                : "";
        if (!dueRemark.isEmpty() && !existingRemarks.contains("Due:")) {
            payroll.setRemarks(existingRemarks.isEmpty() ? dueRemark : existingRemarks + "; " + dueRemark);
        } else if (remarks != null && !remarks.isEmpty()) {
            payroll.setRemarks(existingRemarks.isEmpty() ? remarks : existingRemarks + "; " + remarks);
        }

        // Recalculate totals
        payroll.calculateTotals();

        return payrollRepo.save(payroll);
    }

    /**
     * Calculate previous outstanding due for an employee
     * This looks at previous month's payroll to find any unpaid amounts
     */
    public BigDecimal calculatePreviousDue(String tenantId, String empId, int year, int month) {
        // Get previous month
        java.time.YearMonth currentYm = java.time.YearMonth.of(year, month);
        java.time.YearMonth prevYm = currentYm.minusMonths(1);
        
        // Find previous payroll
        Optional<Payroll> prevPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(
                tenantId, empId, prevYm.getYear(), prevYm.getMonthValue());
        
        if (prevPayroll.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        Payroll prev = prevPayroll.get();
        
        // Check if previous payroll was PAID
        if (prev.getStatus() == PayrollStatus.PAID) {
            return BigDecimal.ZERO; // No due if fully paid
        }
        
        // If previous payroll is APPROVED but not paid, carry forward the net salary as due
        if (prev.getStatus() == PayrollStatus.APPROVED) {
            // This shouldn't normally happen, but if it does, we might want to track it
            return BigDecimal.ZERO;
        }
        
        // If previous payroll is still DRAFT, return 0 (not processed yet)
        return BigDecimal.ZERO;
    }

    /**
     * Get loan info for payroll edit modal
     * Returns loan EMI, overdue dues, and active loans for an employee
     */
    public Map<String, Object> getLoanInfoForPayroll(String tenantId, String empId, int year, int month) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // Get current month's loan EMI
        BigDecimal loanEmi = loanService.getMonthlyEmiDeduction(tenantId, empId);
        result.put("loanEmi", loanEmi);
        
        // Get active loans
        List<com.example.hrms.loan.domain.Loan> activeLoans = loanService.getActiveLoans(tenantId, empId);
        result.put("activeLoansCount", activeLoans.size());
        
        // Get outstanding dues (overdue EMIs) as of the payroll month start date
        // For July 2025 payroll, we want EMIs overdue before July 1, 2025
        LocalDate payrollMonthStart = LocalDate.of(year, month, 1);
        Map<String, Object> overdueInfo = getOutstandingLoanDues(tenantId, empId, payrollMonthStart);
        result.put("overdueDues", overdueInfo.get("totalOverdue"));
        result.put("overdueCount", overdueInfo.get("overdueCount"));
        result.put("overdueEmis", overdueInfo.get("overdueEmis"));
        result.put("overdueAsOfDate", payrollMonthStart.toString());
        
        // Separate fixed EMI loans and flexible loans
        List<Map<String, Object>> fixedEmiLoans = new ArrayList<>();
        List<Map<String, Object>> flexibleLoans = new ArrayList<>();
        BigDecimal totalFlexibleOutstanding = BigDecimal.ZERO;
        
        for (com.example.hrms.loan.domain.Loan loan : activeLoans) {
            Map<String, Object> loanInfo = new LinkedHashMap<>();
            loanInfo.put("loanId", loan.getId());
            loanInfo.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
            loanInfo.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            loanInfo.put("isFlexibleDeduction", Boolean.TRUE.equals(loan.getIsFlexibleDeduction()));
            loanInfo.put("emiAmount", loan.getEmiAmount());
            loanInfo.put("outstandingBalance", loan.getOutstandingBalance());
            loanInfo.put("emisRemaining", (loan.getTenureMonths() != null ? loan.getTenureMonths() : 0) - 
                    (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0));
            loanInfo.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            loanInfo.put("remarks", loan.getRemarks());
            
            if (Boolean.TRUE.equals(loan.getIsFlexibleDeduction())) {
                flexibleLoans.add(loanInfo);
                totalFlexibleOutstanding = totalFlexibleOutstanding.add(
                        loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : BigDecimal.ZERO);
            } else {
                fixedEmiLoans.add(loanInfo);
            }
        }
        result.put("loans", fixedEmiLoans);
        result.put("flexibleLoans", flexibleLoans);
        result.put("flexibleLoansCount", flexibleLoans.size());
        result.put("totalFlexibleOutstanding", totalFlexibleOutstanding);
        
        result.put("empId", empId);
        result.put("tenantId", tenantId);
        result.put("year", year);
        result.put("month", month);
        
        return result;
    }

    /**
     * Get outstanding loan dues for an employee as of a specific date
     * This returns overdue EMIs that haven't been paid before the given date
     * @param asOfDate - the date to check overdue against (typically the payroll month start date)
     *                   If null, uses current date (for UI display of current outstanding)
     */
    public Map<String, Object> getOutstandingLoanDues(String tenantId, String empId, LocalDate asOfDate) {
        List<com.example.hrms.loan.domain.Loan> activeLoans = loanService.getActiveLoans(tenantId, empId);
        
        // Default to current date if not specified
        LocalDate checkDate = asOfDate != null ? asOfDate : LocalDate.now();
        
        BigDecimal totalOverdue = BigDecimal.ZERO;
        List<Map<String, Object>> overdueDetails = new ArrayList<>();
        
        for (com.example.hrms.loan.domain.Loan loan : activeLoans) {
            List<com.example.hrms.loan.domain.LoanRepayment> schedule = loanService.getEmiSchedule(loan.getId());
            
            for (com.example.hrms.loan.domain.LoanRepayment emi : schedule) {
                // Check if EMI is overdue (due date passed before checkDate and not paid)
                if (!Boolean.TRUE.equals(emi.getIsPaid()) && 
                    emi.getDueDate().isBefore(checkDate)) {
                    totalOverdue = totalOverdue.add(emi.getEmiAmount());
                    
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("loanId", loan.getId());
                    detail.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
                    detail.put("emiNumber", emi.getEmiNumber());
                    detail.put("dueDate", emi.getDueDate().toString());
                    detail.put("emiAmount", emi.getEmiAmount());
                    overdueDetails.add(detail);
                }
            }
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("empId", empId);
        result.put("totalOverdue", totalOverdue);
        result.put("overdueCount", overdueDetails.size());
        result.put("overdueEmis", overdueDetails);
        result.put("asOfDate", checkDate.toString());
        
        return result;
    }
    
    /**
     * Overload for backward compatibility - uses current date
     */
    public Map<String, Object> getOutstandingLoanDues(String tenantId, String empId) {
        return getOutstandingLoanDues(tenantId, empId, null);
    }
}
