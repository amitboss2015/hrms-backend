package com.example.hrms.loan.controller;

import com.example.hrms.domain.Employee;
import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.LoanRepayment;
import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.repo.LoanRepository;
import com.example.hrms.loan.repo.LoanRepaymentRepository;
import com.example.hrms.repo.EmployeeRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/loan/reports")
@CrossOrigin(origins = "*")
public class LoanReportsController {

    private final LoanRepository loanRepo;
    private final LoanRepaymentRepository repaymentRepo;
    private final EmployeeRepository employeeRepo;

    public LoanReportsController(LoanRepository loanRepo, 
                                  LoanRepaymentRepository repaymentRepo,
                                  EmployeeRepository employeeRepo) {
        this.loanRepo = loanRepo;
        this.repaymentRepo = repaymentRepo;
        this.employeeRepo = employeeRepo;
    }

    /**
     * Organization-wide loan summary
     */
    @GetMapping("/summary")
    public Map<String, Object> getLoanSummary(@RequestParam String orgId) {
        List<Loan> allLoans = loanRepo.findByOrgIdOrderBySanctionDateDesc(orgId);
        
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
        summary.put("employeesWithActiveLoans", uniqueEmployees);
        
        // By loan type
        Map<String, Long> byType = allLoans.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN",
                        Collectors.counting()));
        summary.put("byLoanType", byType);
        
        return summary;
    }

    /**
     * All active loans with employee details
     */
    @GetMapping("/active")
    public List<Map<String, Object>> getActiveLoans(@RequestParam String orgId) {
        List<Loan> activeLoans = loanRepo.findByOrgIdAndStatusOrderBySanctionDateDesc(orgId, LoanStatus.ACTIVE);
        
        // Get employee details
        Set<String> empIds = activeLoans.stream().map(Loan::getEmpId).collect(Collectors.toSet());
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            employeeRepo.findByEmpCode(empId).ifPresent(e -> empMap.put(empId, e));
        }
        
        return activeLoans.stream().map(loan -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Employee emp = empMap.get(loan.getEmpId());
            
            map.put("loanId", loan.getId());
            map.put("empId", loan.getEmpId());
            map.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
            map.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
            map.put("principalAmount", loan.getPrincipalAmount());
            map.put("emiAmount", loan.getEmiAmount());
            map.put("tenureMonths", loan.getTenureMonths());
            map.put("emisPaid", loan.getEmisPaid());
            map.put("emisRemaining", loan.getTenureMonths() - loan.getEmisPaid());
            map.put("totalPaid", loan.getTotalPaid());
            map.put("outstandingBalance", loan.getOutstandingBalance());
            map.put("sanctionDate", loan.getSanctionDate().toString());
            map.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            map.put("status", loan.getStatus().name());
            
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Employee-wise loan report
     */
    @GetMapping("/employee/{empId}")
    public Map<String, Object> getEmployeeLoanReport(
            @RequestParam String orgId,
            @PathVariable String empId) {
        
        List<Loan> loans = loanRepo.findByOrgIdAndEmpIdOrderBySanctionDateDesc(orgId, empId);
        Employee emp = employeeRepo.findByEmpCode(empId).orElse(null);
        
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
        report.put("totalBorrowed", totalBorrowed);
        report.put("totalPaid", totalPaid);
        report.put("totalOutstanding", totalOutstanding);
        report.put("currentMonthlyEmi", currentMonthlyEmi);
        
        // Loan details
        List<Map<String, Object>> loanDetails = loans.stream().map(loan -> {
            Map<String, Object> loanMap = new LinkedHashMap<>();
            loanMap.put("loanId", loan.getId());
            loanMap.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
            loanMap.put("principalAmount", loan.getPrincipalAmount());
            loanMap.put("interestRate", loan.getInterestRate());
            loanMap.put("emiAmount", loan.getEmiAmount());
            loanMap.put("tenureMonths", loan.getTenureMonths());
            loanMap.put("emisPaid", loan.getEmisPaid());
            loanMap.put("emisRemaining", loan.getTenureMonths() - loan.getEmisPaid());
            loanMap.put("totalPaid", loan.getTotalPaid());
            loanMap.put("outstandingBalance", loan.getOutstandingBalance());
            loanMap.put("sanctionDate", loan.getSanctionDate().toString());
            loanMap.put("status", loan.getStatus().name());
            loanMap.put("remarks", loan.getRemarks());
            return loanMap;
        }).collect(Collectors.toList());
        
        report.put("loans", loanDetails);
        
        return report;
    }

    /**
     * Monthly EMI deduction report (for payroll integration)
     */
    @GetMapping("/monthly-deductions")
    public List<Map<String, Object>> getMonthlyDeductions(
            @RequestParam String orgId,
            @RequestParam int year,
            @RequestParam int month) {
        
        List<Loan> activeLoans = loanRepo.findByOrgIdAndStatusOrderBySanctionDateDesc(orgId, LoanStatus.ACTIVE);
        
        // Get employee details
        Set<String> empIds = activeLoans.stream().map(Loan::getEmpId).collect(Collectors.toSet());
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            employeeRepo.findByEmpCode(empId).ifPresent(e -> empMap.put(empId, e));
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
            
            row.put("empId", empId);
            row.put("empName", emp != null ? getEmployeeName(emp) : empId);
            row.put("activeLoansCount", empLoans.size());
            row.put("totalMonthlyEmi", totalEmi);
            row.put("loans", empLoans.stream().map(l -> Map.of(
                    "loanId", l.getId(),
                    "loanType", l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN",
                    "emiAmount", l.getEmiAmount(),
                    "outstandingBalance", l.getOutstandingBalance()
            )).collect(Collectors.toList()));
            
            return row;
        }).sorted(Comparator.comparing(m -> (String) m.get("empId")))
          .collect(Collectors.toList());
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
        Employee emp = employeeRepo.findByEmpCode(loan.getEmpId()).orElse(null);
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("loanId", loan.getId());
        report.put("empId", loan.getEmpId());
        report.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
        report.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
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
            @RequestParam String orgId,
            @RequestParam(defaultValue = "30") int days) {
        
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        
        List<LoanRepayment> upcomingDues = repaymentRepo.findByIsPaidFalseAndDueDateBetweenOrderByDueDateAsc(today, endDate);
        
        // Get loan and employee details
        Set<Long> loanIds = upcomingDues.stream().map(LoanRepayment::getLoanId).collect(Collectors.toSet());
        Map<Long, Loan> loanMap = new HashMap<>();
        Map<String, Employee> empMap = new HashMap<>();
        
        for (Long loanId : loanIds) {
            loanRepo.findById(loanId).ifPresent(loan -> {
                if (loan.getOrgId().equals(orgId)) {
                    loanMap.put(loanId, loan);
                    employeeRepo.findByEmpCode(loan.getEmpId()).ifPresent(e -> empMap.put(loan.getEmpId(), e));
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
                    row.put("emiNumber", r.getEmiNumber());
                    row.put("dueDate", r.getDueDate().toString());
                    row.put("emiAmount", r.getEmiAmount());
                    row.put("daysUntilDue", java.time.temporal.ChronoUnit.DAYS.between(today, r.getDueDate()));
                    
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
