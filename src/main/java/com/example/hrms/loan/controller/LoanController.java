package com.example.hrms.loan.controller;

import com.example.hrms.domain.Employee;
import com.example.hrms.loan.domain.Loan;
import com.example.hrms.loan.domain.LoanRepayment;
import com.example.hrms.loan.domain.enums.LoanStatus;
import com.example.hrms.loan.domain.enums.LoanType;
import com.example.hrms.loan.domain.enums.RepaymentMode;
import com.example.hrms.loan.service.LoanService;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/loans")
@CrossOrigin(origins = "*")
public class LoanController {

    private final LoanService loanService;
    private final EmployeeRepository employeeRepo;

    public LoanController(LoanService loanService, EmployeeRepository employeeRepo) {
        this.loanService = loanService;
        this.employeeRepo = employeeRepo;
    }
    
    // Helper to find employee using tenant-aware lookup
    private Optional<Employee> findEmployee(String empCode) {
        String tenantId = TenantContext.getTenantId();
        return tenantId != null 
            ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode)
            : employeeRepo.findByEmpCode(empCode);
    }

    /**
     * List all loans or filter by employee
     */
    @GetMapping
    public List<Map<String, Object>> listLoans(@RequestParam String orgId,
                                                @RequestParam(required = false) String empId,
                                                @RequestParam(required = false) String status) {
        List<Loan> loans;
        if (empId != null && !empId.isEmpty()) {
            loans = loanService.getEmployeeLoans(orgId, empId);
        } else {
            loans = loanService.getAllLoans(orgId);
        }
        
        // Filter by status if provided
        if (status != null && !status.isEmpty()) {
            try {
                LoanStatus loanStatus = LoanStatus.valueOf(status.toUpperCase());
                loans = loans.stream().filter(l -> l.getStatus() == loanStatus).toList();
            } catch (Exception ignored) {}
        }
        
        // Enrich with employee names
        Map<String, Employee> empMap = new HashMap<>();
        for (Loan loan : loans) {
            if (!empMap.containsKey(loan.getEmpId())) {
                findEmployee(loan.getEmpId()).ifPresent(e -> empMap.put(loan.getEmpId(), e));
            }
        }
        
        return loans.stream().map(loan -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Employee emp = empMap.get(loan.getEmpId());
            
            map.put("id", loan.getId());
            map.put("orgId", loan.getOrgId());
            map.put("empId", loan.getEmpId());
            map.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
            map.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
            map.put("principalAmount", loan.getPrincipalAmount());
            map.put("interestRate", loan.getInterestRate());
            map.put("tenureMonths", loan.getTenureMonths());
            map.put("emiAmount", loan.getEmiAmount());
            map.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
            map.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
            map.put("totalRepayable", loan.getTotalRepayable());
            map.put("totalPaid", loan.getTotalPaid());
            map.put("outstandingBalance", loan.getOutstandingBalance());
            map.put("emisPaid", loan.getEmisPaid());
            map.put("emisRemaining", loan.getTenureMonths() - loan.getEmisPaid());
            map.put("status", loan.getStatus().name());
            map.put("remarks", loan.getRemarks());
            
            return map;
        }).toList();
    }

    /**
     * Get single loan details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLoan(@PathVariable Long id) {
        Optional<Loan> loanOpt = loanService.getLoan(id);
        if (loanOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Loan loan = loanOpt.get();
        Employee emp = findEmployee(loan.getEmpId()).orElse(null);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", loan.getId());
        result.put("orgId", loan.getOrgId());
        result.put("empId", loan.getEmpId());
        result.put("empName", emp != null ? getEmployeeName(emp) : loan.getEmpId());
        result.put("loanType", loan.getLoanType() != null ? loan.getLoanType().name() : null);
        result.put("principalAmount", loan.getPrincipalAmount());
        result.put("interestRate", loan.getInterestRate());
        result.put("tenureMonths", loan.getTenureMonths());
        result.put("emiAmount", loan.getEmiAmount());
        result.put("sanctionDate", loan.getSanctionDate() != null ? loan.getSanctionDate().toString() : null);
        result.put("firstEmiDate", loan.getFirstEmiDate() != null ? loan.getFirstEmiDate().toString() : null);
        result.put("totalRepayable", loan.getTotalRepayable());
        result.put("totalPaid", loan.getTotalPaid());
        result.put("outstandingBalance", loan.getOutstandingBalance());
        result.put("emisPaid", loan.getEmisPaid());
        result.put("emisRemaining", loan.getTenureMonths() - loan.getEmisPaid());
        result.put("status", loan.getStatus().name());
        result.put("remarks", loan.getRemarks());
        result.put("closedDate", loan.getClosedDate() != null ? loan.getClosedDate().toString() : null);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Admin: Create a new loan for employee
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createLoan(@RequestBody Map<String, Object> request) {
        try {
            Loan loan = new Loan();
            loan.setOrgId((String) request.getOrDefault("orgId", "1"));
            loan.setEmpId((String) request.get("empId"));
            
            // Validate employee exists (tenant-aware lookup)
            if (findEmployee(loan.getEmpId()).isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Employee not found: " + loan.getEmpId()));
            }
            
            // Set loan type
            String loanTypeStr = (String) request.getOrDefault("loanType", "PERSONAL");
            loan.setLoanType(LoanType.valueOf(loanTypeStr.toUpperCase()));
            
            // Set amounts
            loan.setPrincipalAmount(new BigDecimal(request.get("principalAmount").toString()));
            loan.setInterestRate(request.get("interestRate") != null 
                    ? new BigDecimal(request.get("interestRate").toString()) 
                    : BigDecimal.ZERO);
            loan.setTenureMonths(Integer.parseInt(request.get("tenureMonths").toString()));
            
            // EMI can be provided or calculated
            if (request.get("emiAmount") != null) {
                loan.setEmiAmount(new BigDecimal(request.get("emiAmount").toString()));
            }
            
            // Set dates
            if (request.get("sanctionDate") != null) {
                loan.setSanctionDate(LocalDate.parse(request.get("sanctionDate").toString()));
            } else {
                loan.setSanctionDate(LocalDate.now());
            }
            
            if (request.get("firstEmiDate") != null) {
                loan.setFirstEmiDate(LocalDate.parse(request.get("firstEmiDate").toString()));
            }
            
            loan.setRemarks((String) request.get("remarks"));
            
            Loan saved = loanService.createLoan(loan);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Loan created successfully",
                    "loanId", saved.getId(),
                    "emiAmount", saved.getEmiAmount(),
                    "totalRepayable", saved.getTotalRepayable(),
                    "firstEmiDate", saved.getFirstEmiDate().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update loan details (status, remarks)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Loan> updateLoan(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Optional<Loan> loanOpt = loanService.getLoan(id);
        if (loanOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Loan loan = loanOpt.get();
        
        if (updates.get("status") != null) {
            loan.setStatus(LoanStatus.valueOf(updates.get("status").toString().toUpperCase()));
        }
        if (updates.get("remarks") != null) {
            loan.setRemarks(updates.get("remarks").toString());
        }
        
        return ResponseEntity.ok(loanService.updateLoan(id, loan));
    }

    /**
     * Cancel a loan
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancelLoan(@PathVariable Long id) {
        loanService.cancelLoan(id);
        return ResponseEntity.ok(Map.of("message", "Loan cancelled", "loanId", id));
    }

    /**
     * Get EMI schedule for a loan
     */
    @GetMapping("/{id}/schedule")
    public List<Map<String, Object>> getEmiSchedule(@PathVariable Long id) {
        List<LoanRepayment> schedule = loanService.getEmiSchedule(id);
        
        return schedule.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
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
        }).toList();
    }

    /**
     * Get employee's total EMI deduction for current month
     */
    @GetMapping("/employee/{empId}/emi-total")
    public Map<String, Object> getEmployeeEmiTotal(@RequestParam String orgId,
                                                    @PathVariable String empId) {
        BigDecimal monthlyEmi = loanService.getMonthlyEmiDeduction(orgId, empId);
        List<Loan> activeLoans = loanService.getActiveLoans(orgId, empId);
        Employee emp = findEmployee(empId).orElse(null);
        
        return Map.of(
                "empId", empId,
                "empName", emp != null ? getEmployeeName(emp) : empId,
                "monthlyEmiDeduction", monthlyEmi,
                "activeLoansCount", activeLoans.size(),
                "activeLoans", activeLoans.stream().map(l -> Map.of(
                        "id", l.getId(),
                        "loanType", l.getLoanType() != null ? l.getLoanType().name() : "UNKNOWN",
                        "emiAmount", l.getEmiAmount(),
                        "outstandingBalance", l.getOutstandingBalance()
                )).toList()
        );
    }

    /**
     * Get active loans for an employee
     */
    @GetMapping("/employee/{empId}/active")
    public List<Loan> getActiveLoans(@RequestParam String orgId,
                                      @PathVariable String empId) {
        return loanService.getActiveLoans(orgId, empId);
    }

    /**
     * Record manual EMI payment
     */
    @PostMapping("/{id}/pay-emi")
    public ResponseEntity<Map<String, Object>> recordEmiPayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            LoanRepayment repayment = loanService.processEmiPayment(id, null);
            if (repayment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No pending EMI found or loan completed"));
            }
            
            // Update repayment mode if provided
            if (request.get("repaymentMode") != null) {
                repayment.setRepaymentMode(RepaymentMode.valueOf(request.get("repaymentMode").toString().toUpperCase()));
            }
            if (request.get("paymentReference") != null) {
                repayment.setPaymentReference(request.get("paymentReference").toString());
            }
            if (request.get("remarks") != null) {
                repayment.setRemarks(request.get("remarks").toString());
            }
            
            return ResponseEntity.ok(Map.of(
                    "message", "EMI payment recorded",
                    "emiNumber", repayment.getEmiNumber(),
                    "amountPaid", repayment.getAmountPaid(),
                    "paidDate", repayment.getPaidDate().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get loan types (for dropdown)
     */
    @GetMapping("/loan-types")
    public List<Map<String, String>> getLoanTypes() {
        return Arrays.stream(LoanType.values())
                .map(t -> Map.of("value", t.name(), "label", formatLabel(t.name())))
                .toList();
    }

    /**
     * EMI Calculator
     */
    @PostMapping("/calculate-emi")
    public Map<String, Object> calculateEmi(@RequestBody Map<String, Object> request) {
        BigDecimal principal = new BigDecimal(request.get("principalAmount").toString());
        BigDecimal interestRate = request.get("interestRate") != null 
                ? new BigDecimal(request.get("interestRate").toString()) 
                : BigDecimal.ZERO;
        int tenureMonths = Integer.parseInt(request.get("tenureMonths").toString());
        
        BigDecimal emi = Loan.calculateFlatEmi(principal, interestRate, tenureMonths);
        BigDecimal totalRepayable = emi.multiply(BigDecimal.valueOf(tenureMonths));
        BigDecimal totalInterest = totalRepayable.subtract(principal);
        
        return Map.of(
                "principalAmount", principal,
                "interestRate", interestRate,
                "tenureMonths", tenureMonths,
                "emiAmount", emi,
                "totalRepayable", totalRepayable,
                "totalInterest", totalInterest
        );
    }

    private String getEmployeeName(Employee emp) {
        String fn = emp.getFirstName() != null ? emp.getFirstName() : "";
        String ln = emp.getLastName() != null ? emp.getLastName() : "";
        return (fn + " " + ln).trim();
    }

    private String formatLabel(String name) {
        return name.replace("_", " ");
    }
}
