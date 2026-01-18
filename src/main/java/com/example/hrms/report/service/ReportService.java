package com.example.hrms.report.service;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.domain.Employee;
import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.repo.LoanRepository;
import com.example.hrms.loan.repo.LoanRepaymentRepository;
import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceDayRepository attendanceDayRepo;
    private final PayrollRepository payrollRepo;
    private final LoanRepository loanRepo;
    private final LoanRepaymentRepository repaymentRepo;

    public ReportService(EmployeeRepository employeeRepo,
                         AttendanceDayRepository attendanceDayRepo,
                         PayrollRepository payrollRepo,
                         LoanRepository loanRepo,
                         LoanRepaymentRepository repaymentRepo) {
        this.employeeRepo = employeeRepo;
        this.attendanceDayRepo = attendanceDayRepo;
        this.payrollRepo = payrollRepo;
        this.loanRepo = loanRepo;
        this.repaymentRepo = repaymentRepo;
    }
    
    // =========== ATTENDANCE REPORTS ===========

    /**
     * Monthly attendance summary report
     */
    public List<Map<String, Object>> getMonthlyAttendanceReport(String orgId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        List<AttendanceDay> allDays = attendanceDayRepo.findByWorkDateBetween(startDate, endDate);
        List<Employee> employees = employeeRepo.findAll();

        Map<Long, List<AttendanceDay>> byEmployee = allDays.stream()
                .collect(Collectors.groupingBy(AttendanceDay::getEmployeeId));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Employee emp : employees) {
            List<AttendanceDay> empDays = byEmployee.getOrDefault(emp.getId(), Collections.emptyList());

            int present = 0, absent = 0, half = 0, leave = 0;
            int totalWorkMins = 0, totalOtMins = 0;

            for (AttendanceDay ad : empDays) {
                String status = ad.getStatus() != null ? ad.getStatus().toUpperCase() : "";
                switch (status) {
                    case "PRESENT" -> present++;
                    case "ABSENT" -> absent++;
                    case "PARTIAL", "HALF", "HALF_DAY" -> half++;
                    case "LEAVE" -> leave++;
                }
                if (ad.getTotalWorkMin() != null) totalWorkMins += ad.getTotalWorkMin();
                if (ad.getTotalOTEligibleMin() != null) totalOtMins += ad.getTotalOTEligibleMin();
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("empCode", emp.getEmpCode());
            row.put("name", safeName(emp));
            row.put("department", emp.getDepartment());
            row.put("designation", emp.getDesignation());
            row.put("present", present);
            row.put("absent", absent);
            row.put("halfDay", half);
            row.put("leave", leave);
            row.put("totalWorkHours", BigDecimal.valueOf(totalWorkMins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            row.put("overtimeHours", BigDecimal.valueOf(totalOtMins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));

            report.add(row);
        }

        return report;
    }

    /**
     * Daily attendance report for a specific date
     */
    public List<Map<String, Object>> getDailyAttendanceReport(String orgId, LocalDate date) {
        List<AttendanceDay> days = attendanceDayRepo.findByWorkDateBetween(date, date);
        List<Employee> employees = employeeRepo.findAll();

        Map<Long, AttendanceDay> byEmp = days.stream()
                .collect(Collectors.toMap(AttendanceDay::getEmployeeId, ad -> ad, (a, b) -> a));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Employee emp : employees) {
            AttendanceDay ad = byEmp.get(emp.getId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("empCode", emp.getEmpCode());
            row.put("name", safeName(emp));
            row.put("department", emp.getDepartment());
            row.put("status", ad != null ? ad.getStatus() : "NO_DATA");
            row.put("inTime", ad != null && ad.getFirstIn() != null ? ad.getFirstIn().toString() : "-");
            row.put("outTime", ad != null && ad.getLastOut() != null ? ad.getLastOut().toString() : "-");
            row.put("workMins", ad != null && ad.getTotalWorkMin() != null ? ad.getTotalWorkMin() : 0);

            report.add(row);
        }

        return report;
    }

    // =========== PAYROLL REPORTS ===========

    /**
     * Monthly salary sheet (matching the Excel format shared)
     * Uses orgId passed from controller (already resolved)
     */
    public Map<String, Object> getMonthlySalarySheet(String orgId, int year, int month) {
        // Use orgId passed from controller - already resolved by controller
        String tenantId = orgId;
        
        List<Payroll> payrolls = payrollRepo.findByTenantIdAndYearAndMonthOrderByEmpIdAsc(tenantId, year, month);
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);

        Map<String, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getEmpCode, e -> e, (a, b) -> a));
        
        // Get all active loans for this tenant for breakdown
        List<Loan> allLoans = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId);
        Map<String, List<Loan>> loansByEmp = allLoans.stream()
                .filter(l -> l.getStatus() == com.example.hrms.loan.domain.enums.LoanStatus.ACTIVE)
                .collect(Collectors.groupingBy(Loan::getEmpId));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalPf = BigDecimal.ZERO;
        BigDecimal totalEsi = BigDecimal.ZERO;
        BigDecimal totalLoanEmi = BigDecimal.ZERO;
        BigDecimal totalAdvance = BigDecimal.ZERO;
        BigDecimal totalDue = BigDecimal.ZERO;

        int srNo = 1;
        for (Payroll p : payrolls) {
            Employee emp = empMap.get(p.getEmpId());
            List<Loan> empLoans = loansByEmp.getOrDefault(p.getEmpId(), Collections.emptyList());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("srNo", srNo++);
            row.put("empName", p.getEmpName() != null ? p.getEmpName() : (emp != null ? safeName(emp) : p.getEmpId()));
            row.put("empId", p.getEmpId());
            row.put("basicSalary", p.getBasicSalary());
            row.put("increment", p.getIncrement());
            row.put("finalPayment", p.getFinalPayment());
            row.put("workingDays", p.getTotalWorkingDays());
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
            
            // Loan EMI breakdown
            row.put("loanEmi", p.getLoanDeduction());
            row.put("activeLoansCount", empLoans.size());
            
            // Breakdown: Loan EMI vs Manual Advance
            BigDecimal loanEmi = safeAdd(p.getLoanDeduction());
            BigDecimal totalAdv = safeAdd(p.getAdvance());
            BigDecimal manualAdvance = totalAdv.subtract(loanEmi);
            if (manualAdvance.compareTo(BigDecimal.ZERO) < 0) manualAdvance = BigDecimal.ZERO;
            
            row.put("manualAdvance", manualAdvance);
            row.put("advance", p.getAdvance()); // Total ADV = Loan EMI + Manual Advance
            row.put("due", p.getDue());
            row.put("otherDeduction", p.getOtherDeduction());
            row.put("totalDeductions", p.getTotalDeductions());
            row.put("netSalary", p.getNetSalary());
            row.put("status", p.getStatus().name());
            row.put("paymentMode", p.getPaymentMode() != null ? p.getPaymentMode().name() : null);
            row.put("paidDate", p.getPaidDate() != null ? p.getPaidDate().toString() : null);
            row.put("remarks", p.getRemarks());

            rows.add(row);

            totalGross = totalGross.add(safeAdd(p.getGrossSalary()));
            totalNet = totalNet.add(safeAdd(p.getNetSalary()));
            totalPf = totalPf.add(safeAdd(p.getPfEmployee()));
            totalEsi = totalEsi.add(safeAdd(p.getEsiEmployee()));
            totalLoanEmi = totalLoanEmi.add(safeAdd(p.getLoanDeduction()));
            totalAdvance = totalAdvance.add(safeAdd(p.getAdvance()));
            totalDue = totalDue.add(safeAdd(p.getDue()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("tenantId", tenantId);
        result.put("employeeCount", payrolls.size());
        result.put("data", rows);
        result.put("totals", Map.of(
                "totalGross", totalGross,
                "totalNet", totalNet,
                "totalPf", totalPf,
                "totalEsi", totalEsi,
                "totalLoanEmi", totalLoanEmi,
                "totalAdvance", totalAdvance,
                "totalDue", totalDue
        ));

        return result;
    }

    /**
     * EPF contribution report
     */
    public List<Map<String, Object>> getEpfReport(String orgId, int year, int month) {
        String tenantId = orgId;
        
        List<Payroll> payrolls = payrollRepo.findByTenantIdAndYearAndMonthOrderByEmpIdAsc(tenantId, year, month);
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);

        Map<String, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getEmpCode, e -> e, (a, b) -> a));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Payroll p : payrolls) {
            Employee emp = empMap.get(p.getEmpId());
            if (p.getPfEmployee() == null || p.getPfEmployee().compareTo(BigDecimal.ZERO) == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uanNumber", emp != null ? emp.getUanNumber() : "");
            row.put("empCode", p.getEmpId());
            row.put("name", p.getEmpName() != null ? p.getEmpName() : (emp != null ? safeName(emp) : p.getEmpId()));
            row.put("grossWages", p.getGrossSalary());
            row.put("epfWages", p.getFinalPayment()); // PF calculated on final payment (basic + increment)
            row.put("pfEmployee", p.getPfEmployee());
            row.put("pfCompany", p.getPfCompany());
            row.put("totalContribution", safeAdd(p.getPfEmployee()).add(safeAdd(p.getPfCompany())));

            report.add(row);
        }

        return report;
    }

    /**
     * ESIC contribution report (tenant-aware)
     */
    public List<Map<String, Object>> getEsicReport(String orgId, int year, int month) {
        String tenantId = orgId;
        
        List<Payroll> payrolls = payrollRepo.findByTenantIdAndYearAndMonthOrderByEmpIdAsc(tenantId, year, month);
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);

        Map<String, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getEmpCode, e -> e, (a, b) -> a));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Payroll p : payrolls) {
            Employee emp = empMap.get(p.getEmpId());
            if (p.getEsiEmployee() == null || p.getEsiEmployee().compareTo(BigDecimal.ZERO) == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("esicNumber", emp != null ? emp.getEsicNumber() : "");
            row.put("empCode", p.getEmpId());
            row.put("name", p.getEmpName() != null ? p.getEmpName() : (emp != null ? safeName(emp) : p.getEmpId()));
            row.put("grossWages", p.getGrossSalary());
            row.put("esiEmployee", p.getEsiEmployee());
            // ESIC employer is typically 3.25% but we don't store it separately in new schema
            BigDecimal esiEmployer = safeAdd(p.getGrossSalary()).multiply(new BigDecimal("0.0325"))
                    .setScale(0, RoundingMode.CEILING);
            row.put("esiEmployer", esiEmployer);
            row.put("totalContribution", safeAdd(p.getEsiEmployee()).add(esiEmployer));

            report.add(row);
        }

        return report;
    }

    // =========== LOAN REPORTS ===========

    /**
     * Active loans report
     */
    public List<Map<String, Object>> getActiveLoansReport(String orgId) {
        String tenantId = orgId;
        
        List<Loan> loans = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId);
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);

        Map<String, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getEmpCode, e -> e, (a, b) -> a));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Loan loan : loans) {
            Employee emp = empMap.get(loan.getEmpId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("loanId", loan.getId());
            row.put("empCode", loan.getEmpId());
            row.put("name", emp != null ? safeName(emp) : loan.getEmpId());
            row.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
            row.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            row.put("principal", loan.getPrincipalAmount());
            row.put("emiAmount", loan.getEmiAmount());
            row.put("tenure", loan.getTenureMonths());
            row.put("emisPaid", loan.getEmisPaid());
            row.put("emisRemaining", (loan.getTenureMonths() != null ? loan.getTenureMonths() : 0) - 
                    (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0));
            row.put("totalPaid", loan.getTotalPaid());
            row.put("outstanding", loan.getOutstandingBalance());
            row.put("status", loan.getStatus().name());
            row.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            row.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            row.put("remarks", loan.getRemarks());

            report.add(row);
        }

        return report;
    }

    /**
     * Loan deduction report for a month
     */
    public List<Map<String, Object>> getLoanDeductionReport(String orgId, int year, int month) {
        String tenantId = orgId;
        
        List<Payroll> payrolls = payrollRepo.findByTenantIdAndYearAndMonthOrderByEmpIdAsc(tenantId, year, month);
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);

        Map<String, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getEmpCode, e -> e, (a, b) -> a));

        List<Map<String, Object>> report = new ArrayList<>();

        for (Payroll p : payrolls) {
            if (p.getLoanDeduction() == null || p.getLoanDeduction().compareTo(BigDecimal.ZERO) == 0) continue;

            Employee emp = empMap.get(p.getEmpId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("empCode", p.getEmpId());
            row.put("name", p.getEmpName() != null ? p.getEmpName() : (emp != null ? safeName(emp) : p.getEmpId()));
            row.put("loanDeduction", p.getLoanDeduction());
            row.put("grossSalary", p.getGrossSalary());
            row.put("netSalary", p.getNetSalary());

            report.add(row);
        }

        return report;
    }

    // =========== EMPLOYEE PAYSLIP ===========

    /**
     * Generate payslip data for an employee
     * Uses orgId passed from controller (already resolved by controller)
     */
    public Map<String, Object> getPayslip(String orgId, String empId, int year, int month) {
        // Use orgId passed from controller - it's already resolved using TenantContext
        String tenantId = orgId;
        
        Payroll payroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, empId, year, month)
                .orElse(null);
        if (payroll == null) return null;

        // Find employee in the same tenant
        Employee emp = employeeRepo.findByTenantIdAndEmpCode(tenantId, empId).orElse(null);
        if (emp == null) return null;

        Map<String, Object> payslip = new LinkedHashMap<>();

        // Employee Info
        payslip.put("empCode", emp.getEmpCode());
        payslip.put("name", safeName(emp));
        payslip.put("department", emp.getDepartment());
        payslip.put("designation", emp.getDesignation());
        payslip.put("bankAccount", emp.getBankAccount());
        payslip.put("uanNumber", emp.getUanNumber());
        payslip.put("esicNumber", emp.getEsicNumber());
        payslip.put("tenantId", tenantId);

        // Payroll Period
        payslip.put("year", year);
        payslip.put("month", month);

        // Salary Structure
        payslip.put("basicSalary", payroll.getBasicSalary());
        payslip.put("increment", payroll.getIncrement());
        payslip.put("finalPayment", payroll.getFinalPayment());

        // Attendance Summary
        payslip.put("totalWorkingDays", payroll.getTotalWorkingDays());
        payslip.put("presentDays", payroll.getPresentDays());
        payslip.put("absentDays", payroll.getAbsentDays());
        payslip.put("paidLeaveDays", payroll.getPaidLeaveDays());
        payslip.put("weeklyOffDays", payroll.getWeeklyOffDays());
        payslip.put("holidayDays", payroll.getHolidayDays());
        payslip.put("overtimeDays", payroll.getOvertimeDays());
        payslip.put("overtimeHours", payroll.getOvertimeHours());

        // Earnings
        Map<String, BigDecimal> earnings = new LinkedHashMap<>();
        earnings.put("Working Day Amount", payroll.getWorkingDayAmount());
        earnings.put("OT Day Amount", payroll.getOvertimeDayAmount());
        earnings.put("OT Hour Amount", payroll.getOvertimeHourAmount());
        earnings.put("House Rent (HRA)", payroll.getHouseRent());
        earnings.put("Medical Expense", payroll.getMedicalExpense());
        earnings.put("Conveyance", payroll.getConveyanceAllowance());
        earnings.put("Special Allowance", payroll.getSpecialAllowance());
        earnings.put("Other Allowance", payroll.getOtherAllowance());
        earnings.put("Bonus", payroll.getBonus());
        earnings.put("Incentive", payroll.getIncentive());
        payslip.put("earnings", earnings);
        payslip.put("grossSalary", payroll.getGrossSalary());

        // Get active loans for this employee for breakdown
        List<Loan> activeLoans = loanRepo.findByTenantIdAndEmpIdAndStatus(
                tenantId, empId, com.example.hrms.loan.domain.enums.LoanStatus.ACTIVE);
        
        // Loan breakdown
        BigDecimal loanEmi = safeAdd(payroll.getLoanDeduction());
        BigDecimal totalAdv = safeAdd(payroll.getAdvance());
        BigDecimal manualAdvance = totalAdv.subtract(loanEmi);
        if (manualAdvance.compareTo(BigDecimal.ZERO) < 0) manualAdvance = BigDecimal.ZERO;
        
        List<Map<String, Object>> loanDetails = activeLoans.stream().map(loan -> {
            Map<String, Object> loanInfo = new LinkedHashMap<>();
            loanInfo.put("loanId", loan.getId());
            loanInfo.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
            loanInfo.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            loanInfo.put("emiAmount", loan.getEmiAmount());
            loanInfo.put("outstandingBalance", loan.getOutstandingBalance());
            loanInfo.put("emisRemaining", (loan.getTenureMonths() != null ? loan.getTenureMonths() : 0) - 
                    (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0));
            return loanInfo;
        }).toList();

        // Deductions with breakdown
        Map<String, BigDecimal> deductions = new LinkedHashMap<>();
        deductions.put("ESI (0.75%)", payroll.getEsiEmployee());
        deductions.put("PF Own (6%)", payroll.getPfEmployee());
        deductions.put("Loan EMI", loanEmi);
        deductions.put("Manual Advance", manualAdvance);
        deductions.put("Total Advance (ADV)", payroll.getAdvance());
        deductions.put("Due (Previous Outstanding)", payroll.getDue());
        deductions.put("Professional Tax", payroll.getProfessionalTax());
        deductions.put("TDS", payroll.getTds());
        deductions.put("Other Deduction", payroll.getOtherDeduction());
        payslip.put("deductions", deductions);
        payslip.put("totalDeductions", payroll.getTotalDeductions());
        
        // Loan info section
        payslip.put("loanInfo", Map.of(
                "activeLoansCount", activeLoans.size(),
                "totalLoanEmi", loanEmi,
                "manualAdvance", manualAdvance,
                "loans", loanDetails
        ));

        // Net Pay
        payslip.put("netSalary", payroll.getNetSalary());
        payslip.put("status", payroll.getStatus().name());
        payslip.put("paymentMode", payroll.getPaymentMode() != null ? payroll.getPaymentMode().name() : null);
        payslip.put("paidDate", payroll.getPaidDate() != null ? payroll.getPaidDate().toString() : null);
        payslip.put("transactionReference", payroll.getTransactionReference());
        payslip.put("remarks", payroll.getRemarks());

        // Company Contribution (for reference)
        payslip.put("pfCompany", payroll.getPfCompany());

        return payslip;
    }

    // =========== HELPER METHODS ===========

    private String safeName(Employee emp) {
        if (emp == null) return "";
        String fn = emp.getFirstName() != null ? emp.getFirstName() : "";
        String ln = emp.getLastName() != null ? emp.getLastName() : "";
        String name = (fn + " " + ln).trim();
        return name.isEmpty() ? emp.getEmpCode() : name;
    }

    private BigDecimal safeAdd(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
