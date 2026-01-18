package com.example.hrms.loan.controller;

import com.example.hrms.domain.Employee;
import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.LoanRepayment;
import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.repo.LoanRepository;
import com.example.hrms.loan.repo.LoanRepaymentRepository;
import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loan Reports Controller - Admin-focused reports for loan management
 * 
 * Reports available:
 * 1. Organization Summary Dashboard - Total loans, outstanding, recovered
 * 2. Month/Year Report - All EMI deductions for a specific month with payroll linkage
 * 3. Employee Report - Complete loan history for an employee
 * 4. Active Loans - All currently active loans
 * 5. Monthly Deductions - Expected EMI for upcoming payroll
 * 6. Upcoming EMIs - EMIs due in next N days
 * 7. Loan Status Report - Detailed status of each loan
 */
@RestController
@RequestMapping("/api/loan/reports")
@CrossOrigin(origins = "*")
public class LoanReportsController {

    private final LoanRepository loanRepo;
    private final LoanRepaymentRepository repaymentRepo;
    private final EmployeeRepository employeeRepo;
    private final PayrollRepository payrollRepo;

    public LoanReportsController(LoanRepository loanRepo, 
                                  LoanRepaymentRepository repaymentRepo,
                                  EmployeeRepository employeeRepo,
                                  PayrollRepository payrollRepo) {
        this.loanRepo = loanRepo;
        this.repaymentRepo = repaymentRepo;
        this.employeeRepo = employeeRepo;
        this.payrollRepo = payrollRepo;
    }
    
    // Get effective tenant ID (from context or fallback to param)
    private String getEffectiveTenantId(String orgIdParam) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }
        return orgIdParam;
    }
    
    // Helper to find employee using tenant-aware lookup
    private Optional<Employee> findEmployee(String empCode) {
        String tenantId = TenantContext.getTenantId();
        return tenantId != null 
            ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode)
            : employeeRepo.findByEmpCode(empCode);
    }

    /**
     * Organization-wide loan summary dashboard
     * Shows: Total loans, active, closed, disbursed, outstanding, recovered
     */
    @GetMapping("/summary")
    public Map<String, Object> getLoanSummary(@RequestParam(required = false) String orgId) {
        String tenantId = getEffectiveTenantId(orgId);
        List<Loan> allLoans = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId);
        
        Map<String, Object> summary = new LinkedHashMap<>();
        
        // Total loans
        summary.put("totalLoans", allLoans.size());
        
        // Active loans
        long activeCount = allLoans.stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE).count();
        summary.put("activeLoans", activeCount);
        
        // Closed loans
        long closedCount = allLoans.stream().filter(l -> l.getStatus() == LoanStatus.CLOSED).count();
        summary.put("closedLoans", closedCount);
        
        // Total principal disbursed
        BigDecimal totalDisbursed = allLoans.stream()
                .map(Loan::getPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.put("totalDisbursed", totalDisbursed);
        
        // Total outstanding (only active loans)
        BigDecimal totalOutstanding = allLoans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.put("totalOutstanding", totalOutstanding);
        
        // Total recovered
        BigDecimal totalRecovered = allLoans.stream()
                .map(Loan::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.put("totalRecovered", totalRecovered);
        
        // Monthly EMI burden (all active loans)
        BigDecimal monthlyEmi = allLoans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getEmiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.put("totalMonthlyEmi", monthlyEmi);
        
        // Unique employees with active loans
        long uniqueEmployees = allLoans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getEmpId)
                .distinct()
                .count();
        summary.put("employeesWithLoans", uniqueEmployees);
        
        // By loan type
        Map<String, Long> byType = allLoans.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN",
                        Collectors.counting()));
        summary.put("byLoanType", byType);
        
        return summary;
    }

    /**
     * Month/Year wise EMI deduction report
     * Shows all loan deductions for a specific month, with payroll linkage
     */
    @GetMapping("/monthly")
    public Map<String, Object> getMonthlyLoanReport(
            @RequestParam(required = false) String orgId,
            @RequestParam int year,
            @RequestParam int month) {
        
        String tenantId = getEffectiveTenantId(orgId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("year", year);
        report.put("month", month);
        report.put("monthName", ym.getMonth().toString());
        
        // Get all repayments paid in this month
        List<LoanRepayment> paidInMonth = repaymentRepo.findByPaidDateBetween(startDate, endDate);
        
        // Get all repayments due in this month (for expected deductions)
        List<LoanRepayment> dueInMonth = repaymentRepo.findByDueDateBetween(startDate, endDate);
        
        // Get payroll data for this month to show actual deductions
        List<Payroll> payrolls = payrollRepo.findByTenantIdAndYearAndMonth(tenantId, year, month);
        
        // Filter repayments by tenant
        Set<Long> tenantLoanIds = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId)
                .stream().map(Loan::getId).collect(Collectors.toSet());
        
        paidInMonth = paidInMonth.stream()
                .filter(r -> tenantLoanIds.contains(r.getLoanId()))
                .toList();
        dueInMonth = dueInMonth.stream()
                .filter(r -> tenantLoanIds.contains(r.getLoanId()))
                .toList();
        
        // Summary stats
        BigDecimal totalExpected = dueInMonth.stream()
                .map(LoanRepayment::getEmiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalDeducted = paidInMonth.stream()
                .map(LoanRepayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalFromPayroll = payrolls.stream()
                .map(p -> p.getLoanDeduction() != null ? p.getLoanDeduction() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long pendingCount = dueInMonth.stream().filter(r -> !Boolean.TRUE.equals(r.getIsPaid())).count();
        long paidCount = dueInMonth.stream().filter(r -> Boolean.TRUE.equals(r.getIsPaid())).count();
        
        report.put("totalExpectedEmi", totalExpected);
        report.put("totalDeducted", totalDeducted);
        report.put("totalFromPayroll", totalFromPayroll);
        report.put("totalDueEmiCount", dueInMonth.size());
        report.put("paidEmiCount", paidCount);
        report.put("pendingEmiCount", pendingCount);
        
        // Detail by employee
        Map<String, List<LoanRepayment>> byEmployee = new HashMap<>();
        Map<Long, Loan> loanCache = new HashMap<>();
        
        for (LoanRepayment r : dueInMonth) {
            Loan loan = loanCache.computeIfAbsent(r.getLoanId(), 
                    id -> loanRepo.findById(id).orElse(null));
            if (loan != null) {
                byEmployee.computeIfAbsent(loan.getEmpId(), k -> new ArrayList<>()).add(r);
            }
        }
        
        // Get employee details
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : byEmployee.keySet()) {
            findEmployee(empId).ifPresent(e -> empMap.put(empId, e));
        }
        
        // Build employee-wise breakdown
        List<Map<String, Object>> employeeDeductions = new ArrayList<>();
        for (Map.Entry<String, List<LoanRepayment>> entry : byEmployee.entrySet()) {
            String empId = entry.getKey();
            List<LoanRepayment> empRepayments = entry.getValue();
            Employee emp = empMap.get(empId);
            
            Map<String, Object> empRow = new LinkedHashMap<>();
            empRow.put("empId", empId);
            empRow.put("empName", emp != null ? getEmployeeName(emp) : empId);
            
            BigDecimal empTotalExpected = empRepayments.stream()
                    .map(LoanRepayment::getEmiAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal empTotalPaid = empRepayments.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsPaid()))
                    .map(LoanRepayment::getAmountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            empRow.put("totalExpected", empTotalExpected);
            empRow.put("totalPaid", empTotalPaid);
            empRow.put("pending", empTotalExpected.subtract(empTotalPaid));
            empRow.put("emisCount", empRepayments.size());
            empRow.put("paidCount", empRepayments.stream().filter(r -> Boolean.TRUE.equals(r.getIsPaid())).count());
            
            // Loan details
            List<Map<String, Object>> loanDetails = new ArrayList<>();
            for (LoanRepayment r : empRepayments) {
                Loan loan = loanCache.get(r.getLoanId());
                if (loan != null) {
                    Map<String, Object> loanRow = new LinkedHashMap<>();
                    loanRow.put("loanId", loan.getId());
                    loanRow.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
                    loanRow.put("emiNumber", r.getEmiNumber());
                    loanRow.put("emiAmount", r.getEmiAmount());
                    loanRow.put("dueDate", r.getDueDate().toString());
                    loanRow.put("isPaid", r.getIsPaid());
                    loanRow.put("paidDate", r.getPaidDate() != null ? r.getPaidDate().toString() : null);
                    loanRow.put("repaymentMode", r.getRepaymentMode() != null ? r.getRepaymentMode().name() : null);
                    loanRow.put("outstandingAfter", r.getBalanceAfterPayment());
                    loanDetails.add(loanRow);
                }
            }
            empRow.put("loans", loanDetails);
            employeeDeductions.add(empRow);
        }
        
        // Sort by employee ID
        employeeDeductions.sort(Comparator.comparing(m -> (String) m.get("empId")));
        report.put("employeeDeductions", employeeDeductions);
        
        return report;
    }

    /**
     * All active loans with employee details
     */
    @GetMapping("/active")
    public List<Map<String, Object>> getActiveLoans(@RequestParam(required = false) String orgId) {
        String tenantId = getEffectiveTenantId(orgId);
        List<Loan> activeLoans = loanRepo.findByTenantIdAndStatusOrderBySanctionDateDesc(tenantId, LoanStatus.ACTIVE);
        
        // Get employee details
        Set<String> empIds = activeLoans.stream().map(Loan::getEmpId).collect(Collectors.toSet());
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            findEmployee(empId).ifPresent(e -> empMap.put(empId, e));
        }
        
        return activeLoans.stream().map(loan -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Employee emp = empMap.get(loan.getEmpId());
            
            map.put("loanId", loan.getId());
            map.put("empId", loan.getEmpId());
            map.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
            map.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
            map.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            map.put("principalAmount", loan.getPrincipalAmount());
            map.put("emiAmount", loan.getEmiAmount());
            map.put("tenureMonths", loan.getTenureMonths());
            map.put("emisPaid", loan.getEmisPaid());
            map.put("emisRemaining", (loan.getTenureMonths() != null ? loan.getTenureMonths() : 0) - 
                    (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0));
            map.put("totalPaid", loan.getTotalPaid());
            map.put("outstandingBalance", loan.getOutstandingBalance());
            map.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            map.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            map.put("status", loan.getStatus().name());
            map.put("remarks", loan.getRemarks());
            
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Employee-wise comprehensive loan report
     */
    @GetMapping("/employee/{empId}")
    public Map<String, Object> getEmployeeLoanReport(
            @RequestParam(required = false) String orgId,
            @PathVariable String empId) {
        
        String tenantId = getEffectiveTenantId(orgId);
        List<Loan> loans = loanRepo.findByTenantIdAndEmpIdOrderBySanctionDateDesc(tenantId, empId);
        Employee emp = findEmployee(empId).orElse(null);
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("empId", empId);
        report.put("empName", emp != null ? getEmployeeName(emp) : empId);
        
        // Summary
        BigDecimal totalBorrowed = loans.stream()
                .map(Loan::getPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = loans.stream()
                .map(Loan::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = loans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentMonthlyEmi = loans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getEmiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        report.put("totalLoans", loans.size());
        report.put("activeLoans", loans.stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE).count());
        report.put("closedLoans", loans.stream().filter(l -> l.getStatus() == LoanStatus.CLOSED).count());
        report.put("totalBorrowed", totalBorrowed);
        report.put("totalPaid", totalPaid);
        report.put("totalOutstanding", totalOutstanding);
        report.put("currentMonthlyEmi", currentMonthlyEmi);
        
        // Loan details with repayment history
        List<Map<String, Object>> loanDetails = loans.stream().map(loan -> {
            Map<String, Object> loanMap = new LinkedHashMap<>();
            loanMap.put("loanId", loan.getId());
            loanMap.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
            loanMap.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            loanMap.put("principalAmount", loan.getPrincipalAmount());
            loanMap.put("interestRate", loan.getInterestRate());
            loanMap.put("emiAmount", loan.getEmiAmount());
            loanMap.put("tenureMonths", loan.getTenureMonths());
            loanMap.put("emisPaid", loan.getEmisPaid());
            loanMap.put("emisRemaining", (loan.getTenureMonths() != null ? loan.getTenureMonths() : 0) - 
                    (loan.getEmisPaid() != null ? loan.getEmisPaid() : 0));
            loanMap.put("totalPaid", loan.getTotalPaid());
            loanMap.put("outstandingBalance", loan.getOutstandingBalance());
            loanMap.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            loanMap.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            loanMap.put("status", loan.getStatus().name());
            loanMap.put("remarks", loan.getRemarks());
            
            // Last 5 repayments
            List<LoanRepayment> recentRepayments = repaymentRepo.findByLoanIdOrderByEmiNumberAsc(loan.getId())
                    .stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsPaid()))
                    .sorted((a, b) -> b.getEmiNumber().compareTo(a.getEmiNumber()))
                    .limit(5)
                    .toList();
            
            loanMap.put("recentRepayments", recentRepayments.stream().map(r -> Map.of(
                    "emiNumber", r.getEmiNumber(),
                    "paidDate", r.getPaidDate() != null ? r.getPaidDate().toString() : null,
                    "amount", r.getAmountPaid()
            )).toList());
            
            return loanMap;
        }).collect(Collectors.toList());
        
        report.put("loans", loanDetails);
        
        return report;
    }

    /**
     * Monthly EMI deduction forecast (for payroll planning)
     * Shows expected EMI deductions for upcoming payroll
     */
    @GetMapping("/monthly-deductions")
    public List<Map<String, Object>> getMonthlyDeductions(
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        
        String tenantId = getEffectiveTenantId(orgId);
        
        // Default to current month if not specified
        YearMonth ym = (year != null && month != null) 
                ? YearMonth.of(year, month) 
                : YearMonth.now();
        
        List<Loan> activeLoans = loanRepo.findByTenantIdAndStatusOrderBySanctionDateDesc(tenantId, LoanStatus.ACTIVE);
        
        // Get employee details
        Set<String> empIds = activeLoans.stream().map(Loan::getEmpId).collect(Collectors.toSet());
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            findEmployee(empId).ifPresent(e -> empMap.put(empId, e));
        }
        
        // Group by employee
        Map<String, List<Loan>> byEmployee = activeLoans.stream()
                .collect(Collectors.groupingBy(Loan::getEmpId));
        
        return byEmployee.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            String empId = entry.getKey();
            List<Loan> empLoans = entry.getValue();
            Employee emp = empMap.get(empId);
            
            BigDecimal totalEmi = empLoans.stream()
                    .map(Loan::getEmiAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalOutstanding = empLoans.stream()
                    .map(Loan::getOutstandingBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            row.put("empId", empId);
            row.put("empName", emp != null ? getEmployeeName(emp) : empId);
            row.put("activeLoansCount", empLoans.size());
            row.put("totalMonthlyEmi", totalEmi);
            row.put("totalOutstanding", totalOutstanding);
            row.put("forMonth", ym.toString());
            row.put("loans", empLoans.stream().map(l -> {
                Map<String, Object> loanMap = new LinkedHashMap<>();
                loanMap.put("loanId", l.getId());
                loanMap.put("loanType", l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN");
                loanMap.put("isOneTimeDeduction", Boolean.TRUE.equals(l.getIsOneTimeDeduction()));
                loanMap.put("emiAmount", l.getEmiAmount());
                loanMap.put("outstandingBalance", l.getOutstandingBalance());
                loanMap.put("emisRemaining", (l.getTenureMonths() != null ? l.getTenureMonths() : 0) - 
                        (l.getEmisPaid() != null ? l.getEmisPaid() : 0));
                return loanMap;
            }).collect(Collectors.toList()));
            
            return row;
        }).sorted(Comparator.comparing(m -> (String) m.get("empId")))
          .collect(Collectors.toList());
    }

    /**
     * Loan status report - Detailed status of all loans
     */
    @GetMapping("/status")
    public List<Map<String, Object>> getLoanStatusReport(
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) String status) {
        
        String tenantId = getEffectiveTenantId(orgId);
        
        List<Loan> loans;
        if (status != null && !status.isEmpty()) {
            try {
                LoanStatus loanStatus = LoanStatus.valueOf(status.toUpperCase());
                loans = loanRepo.findByTenantIdAndStatusOrderBySanctionDateDesc(tenantId, loanStatus);
            } catch (IllegalArgumentException e) {
                loans = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId);
            }
        } else {
            loans = loanRepo.findByTenantIdOrderBySanctionDateDesc(tenantId);
        }
        
        // Get employee details
        Set<String> empIds = loans.stream().map(Loan::getEmpId).collect(Collectors.toSet());
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            findEmployee(empId).ifPresent(e -> empMap.put(empId, e));
        }
        
        return loans.stream().map(loan -> {
            Map<String, Object> row = new LinkedHashMap<>();
            Employee emp = empMap.get(loan.getEmpId());
            
            row.put("loanId", loan.getId());
            row.put("empId", loan.getEmpId());
            row.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
            row.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : "UNKNOWN");
            row.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
            row.put("principalAmount", loan.getPrincipalAmount());
            row.put("interestRate", loan.getInterestRate());
            row.put("emiAmount", loan.getEmiAmount());
            row.put("tenureMonths", loan.getTenureMonths());
            row.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            row.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            
            // Progress
            int totalEmis = loan.getTenureMonths() != null ? loan.getTenureMonths() : 0;
            int paidEmis = loan.getEmisPaid() != null ? loan.getEmisPaid() : 0;
            row.put("totalEmis", totalEmis);
            row.put("emisPaid", paidEmis);
            row.put("emisRemaining", totalEmis - paidEmis);
            row.put("progressPercent", totalEmis > 0 ? Math.round((paidEmis * 100.0) / totalEmis) : 0);
            
            // Amounts
            row.put("totalRepayable", loan.getTotalRepayable());
            row.put("totalPaid", loan.getTotalPaid());
            row.put("outstandingBalance", loan.getOutstandingBalance());
            
            // Status
            row.put("status", loan.getStatus().name());
            row.put("closedDate", loan.getClosedDate() != null ? loan.getClosedDate().toString() : null);
            row.put("remarks", loan.getRemarks());
            
            return row;
        }).collect(Collectors.toList());
    }

    /**
     * Loan repayment history for a specific loan
     */
    @GetMapping("/{loanId}/repayments")
    public Map<String, Object> getLoanRepaymentHistory(@PathVariable Long loanId) {
        Loan loan = loanRepo.findById(loanId).orElse(null);
        if (loan == null) {
            return Map.of("error", "Loan not found");
        }
        
        List<LoanRepayment> repayments = repaymentRepo.findByLoanIdOrderByEmiNumberAsc(loanId);
        Employee emp = findEmployee(loan.getEmpId()).orElse(null);
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("loanId", loan.getId());
        report.put("empId", loan.getEmpId());
        report.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
        report.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
        report.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
        report.put("principalAmount", loan.getPrincipalAmount());
        report.put("totalRepayable", loan.getTotalRepayable());
        report.put("emiAmount", loan.getEmiAmount());
        report.put("tenureMonths", loan.getTenureMonths());
        report.put("emisPaid", loan.getEmisPaid());
        report.put("totalPaid", loan.getTotalPaid());
        report.put("outstandingBalance", loan.getOutstandingBalance());
        report.put("status", loan.getStatus().name());
        
        List<Map<String, Object>> schedule = repayments.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("emiNumber", r.getEmiNumber());
            row.put("dueDate", r.getDueDate().toString());
            row.put("emiAmount", r.getEmiAmount());
            row.put("principalComponent", r.getPrincipalComponent());
            row.put("interestComponent", r.getInterestComponent());
            row.put("amountPaid", r.getAmountPaid());
            row.put("paidDate", r.getPaidDate() != null ? r.getPaidDate().toString() : null);
            row.put("isPaid", r.getIsPaid());
            row.put("repaymentMode", r.getRepaymentMode() != null ? r.getRepaymentMode().name() : null);
            row.put("balanceAfterPayment", r.getBalanceAfterPayment());
            row.put("payrollId", r.getPayrollId());
            return row;
        }).collect(Collectors.toList());
        
        report.put("repaymentSchedule", schedule);
        
        return report;
    }

    /**
     * Upcoming EMI dues in next N days
     */
    @GetMapping("/upcoming-dues")
    public List<Map<String, Object>> getUpcomingDues(
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "30") int days) {
        
        String tenantId = getEffectiveTenantId(orgId);
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        
        List<LoanRepayment> upcomingDues = repaymentRepo.findByIsPaidFalseAndDueDateBetweenOrderByDueDateAsc(today, endDate);
        
        // Get loan and employee details
        Set<Long> loanIds = upcomingDues.stream().map(LoanRepayment::getLoanId).collect(Collectors.toSet());
        Map<Long, Loan> loanMap = new HashMap<>();
        Map<String, Employee> empMap = new HashMap<>();
        
        for (Long loanId : loanIds) {
            loanRepo.findById(loanId).ifPresent(loan -> {
                if (tenantId.equals(loan.getTenantId())) {
                    loanMap.put(loanId, loan);
                    findEmployee(loan.getEmpId()).ifPresent(e -> empMap.put(loan.getEmpId(), e));
                }
            });
        }
        
        return upcomingDues.stream()
                .filter(r -> loanMap.containsKey(r.getLoanId()))
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Loan loan = loanMap.get(r.getLoanId());
                    Employee emp = empMap.get(loan.getEmpId());
                    
                    row.put("repaymentId", r.getId());
                    row.put("loanId", loan.getId());
                    row.put("empId", loan.getEmpId());
                    row.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
                    row.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
                    row.put("isOneTimeDeduction", Boolean.TRUE.equals(loan.getIsOneTimeDeduction()));
                    row.put("emiNumber", r.getEmiNumber());
                    row.put("dueDate", r.getDueDate().toString());
                    row.put("emiAmount", r.getEmiAmount());
                    row.put("daysUntilDue", java.time.temporal.ChronoUnit.DAYS.between(today, r.getDueDate()));
                    row.put("outstandingBalance", loan.getOutstandingBalance());
                    
                    return row;
                })
                .collect(Collectors.toList());
    }

    private String getEmployeeName(Employee emp) {
        String fn = emp.getFirstName() != null ? emp.getFirstName() : "";
        String ln = emp.getLastName() != null ? emp.getLastName() : "";
        return (fn + " " + ln).trim();
    }
}
