package com.example.hrms.payroll.controller;

import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.domain.enums.PaymentMode;
import com.example.hrms.payroll.service.PayrollService;
import com.example.hrms.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@Slf4j
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    /**
     * Helper to resolve orgId - uses TenantContext if orgId is null/empty/default
     */
    private String resolveOrgId(String orgId) {
        if (orgId == null || orgId.isEmpty() || "ORG001".equals(orgId)) {
            return TenantContext.getTenantId();
        }
        return orgId;
    }

    // ============ PRE-CHECK ============

    /**
     * Check if attendance is available for payroll generation
     */
    @GetMapping("/check-attendance")
    public ResponseEntity<Map<String, Object>> checkAttendanceAvailability(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.checkAttendanceAvailability(resolveOrgId(orgId), year, month));
    }

    // ============ GENERATION ============

    /**
     * Generate payroll for all employees for a month
     */
    @PostMapping(value = "/generate", produces = "application/json")
    public ResponseEntity<?> generatePayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        log.info("🎯🎯🎯 ENTERING generatePayroll endpoint! orgId={}, year={}, month={}", orgId, year, month);
        
        // Use TenantContext if orgId not provided
        if (orgId == null || orgId.isEmpty()) {
            orgId = TenantContext.getTenantId();
            log.info("📍 Using TenantContext orgId: {}", orgId);
        }
        log.info("🎯 Generating payroll for orgId={}, year={}, month={}", orgId, year, month);
        
        // First check if attendance is available
        Map<String, Object> attendanceCheck = payrollService.checkAttendanceAvailability(orgId, year, month);
        if (!(Boolean) attendanceCheck.get("available")) {
            return ResponseEntity.badRequest().body(attendanceCheck);
        }
        
        return ResponseEntity.ok(payrollService.generateMonthlyPayroll(orgId, year, month));
    }

    /**
     * Generate payroll for a single employee
     */
    @PostMapping("/generate/{empId}")
    public ResponseEntity<?> generateEmployeePayroll(
            @RequestParam(required = false) String orgId,
            @PathVariable String empId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        String resolvedOrgId = resolveOrgId(orgId);
        
        // Check if attendance is available for this employee
        Map<String, Object> attendanceCheck = payrollService.checkEmployeeAttendance(resolvedOrgId, empId, year, month);
        if (!(Boolean) attendanceCheck.get("available")) {
            return ResponseEntity.badRequest().body(attendanceCheck);
        }
        
        return ResponseEntity.ok(payrollService.generatePayroll(resolvedOrgId, empId, year, month));
    }

    /**
     * Recalculate payroll for a single employee (delete existing and regenerate)
     * This is useful when employee data (salary, shift, etc.) has changed
     */
    @PostMapping("/recalculate/{empId}")
    public ResponseEntity<?> recalculateEmployeePayroll(
            @RequestParam(required = false) String orgId,
            @PathVariable String empId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        String resolvedOrgId = resolveOrgId(orgId);
        log.info("Recalculating payroll for employee={}, org={}, year={}, month={}", 
                empId, resolvedOrgId, year, month);
        
        try {
            // Delete existing payroll for this employee (if any)
            payrollService.deleteEmployeePayroll(resolvedOrgId, empId, year, month);
            
            // Check if attendance is available
            Map<String, Object> attendanceCheck = payrollService.checkEmployeeAttendance(resolvedOrgId, empId, year, month);
            if (!(Boolean) attendanceCheck.get("available")) {
                return ResponseEntity.badRequest().body(attendanceCheck);
            }
            
            // Generate fresh payroll
            Payroll payroll = payrollService.generatePayroll(resolvedOrgId, empId, year, month);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payroll recalculated successfully");
            response.put("payroll", payroll);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error recalculating payroll: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============ RETRIEVAL ============

    /**
     * Get monthly payroll for all employees
     */
    @GetMapping
    public ResponseEntity<List<Payroll>> getMonthlyPayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.getMonthlyPayroll(resolveOrgId(orgId), year, month));
    }

    /**
     * Get payroll by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Payroll> getPayroll(@PathVariable Long id) {
        return payrollService.getPayroll(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get employee payroll history
     */
    @GetMapping("/employee/{empId}")
    public ResponseEntity<List<Payroll>> getEmployeePayrollHistory(
            @RequestParam(required = false) String orgId,
            @PathVariable String empId) {
        return ResponseEntity.ok(payrollService.getEmployeePayrollHistory(resolveOrgId(orgId), empId));
    }

    /**
     * Get payroll summary for a month
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPayrollSummary(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.getPayrollSummary(resolveOrgId(orgId), year, month));
    }

    /**
     * Get list of employees who were skipped (no attendance for the month)
     */
    @GetMapping("/skipped")
    public ResponseEntity<Map<String, Object>> getSkippedEmployees(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.getSkippedEmployees(resolveOrgId(orgId), year, month));
    }

    /**
     * Get detailed payroll with loan, leave, and advance info for verification
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<Map<String, Object>> getPayrollDetails(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getPayrollDetails(id));
    }

    /**
     * Get all payrolls with detailed info for a month (for admin verification)
     */
    @GetMapping("/detailed")
    public ResponseEntity<List<Map<String, Object>>> getDetailedMonthlyPayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.getDetailedMonthlyPayroll(resolveOrgId(orgId), year, month));
    }

    /**
     * Get only PAID payrolls for salary sheet display
     */
    @GetMapping("/paid")
    public ResponseEntity<List<Payroll>> getPaidPayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return ResponseEntity.ok(payrollService.getPaidPayroll(resolveOrgId(orgId), year, month));
    }

    // ============ UPDATE ============

    /**
     * Update payroll (for manual adjustments like advance, due, bonus)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Payroll> updatePayroll(
            @PathVariable Long id,
            @RequestBody Payroll updates) {
        try {
            return ResponseEntity.ok(payrollService.updatePayroll(id, updates));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update advance amount for an employee (Admin can give advance)
     * This advance will be tracked and deducted from salary
     */
    @PutMapping("/{id}/advance")
    public ResponseEntity<Payroll> updateAdvance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            BigDecimal manualAdvance = new BigDecimal(request.get("manualAdvance").toString());
            String remarks = (String) request.getOrDefault("remarks", "");
            return ResponseEntity.ok(payrollService.updateManualAdvance(id, manualAdvance, remarks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update due amount for an employee (Previous outstanding dues)
     */
    @PutMapping("/{id}/due")
    public ResponseEntity<Payroll> updateDue(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            BigDecimal due = new BigDecimal(request.get("due").toString());
            String remarks = (String) request.getOrDefault("remarks", "");
            return ResponseEntity.ok(payrollService.updateDue(id, due, remarks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update flexible loan deduction for an employee
     * Admin can adjust how much to deduct from flexible loans
     */
    @PutMapping("/{id}/flexible-loan")
    public ResponseEntity<?> updateFlexibleLoanDeduction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            Long loanId = request.get("loanId") != null ? 
                    Long.parseLong(request.get("loanId").toString()) : null;
            String remarks = (String) request.getOrDefault("remarks", "");
            
            return ResponseEntity.ok(payrollService.updateFlexibleLoanDeduction(id, amount, loanId, remarks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get outstanding loan dues for an employee
     * Returns overdue EMIs that need to be collected
     */
    @GetMapping("/outstanding-dues/{empId}")
    public ResponseEntity<Map<String, Object>> getOutstandingDues(
            @PathVariable String empId) {
        String tenantId = com.example.hrms.tenant.TenantContext.getTenantId();
        return ResponseEntity.ok(payrollService.getOutstandingLoanDues(tenantId, empId));
    }

    /**
     * Get loan and due info for an employee (for Edit Payroll modal)
     * Returns current loan EMI, overdue dues, and active loans
     */
    @GetMapping("/loan-info/{empId}")
    public ResponseEntity<Map<String, Object>> getLoanInfoForPayroll(
            @PathVariable String empId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        String tenantId = com.example.hrms.tenant.TenantContext.getTenantId();
        return ResponseEntity.ok(payrollService.getLoanInfoForPayroll(tenantId, empId, year, month));
    }

    // ============ APPROVAL ============

    /**
     * Approve single payroll
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Payroll> approvePayroll(
            @PathVariable Long id,
            @RequestParam(required = false) String approvedBy) {
        return ResponseEntity.ok(payrollService.approvePayroll(id, approvedBy));
    }

    /**
     * Approve all payrolls for a month
     */
    @PostMapping("/approve-all")
    public ResponseEntity<List<Payroll>> approveMonthlyPayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) String approvedBy) {
        return ResponseEntity.ok(payrollService.approveMonthlyPayroll(resolveOrgId(orgId), year, month, approvedBy));
    }

    // ============ PAYMENT ============

    /**
     * Mark single payroll as paid with payment details
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<Payroll> markAsPaid(
            @PathVariable Long id,
            @RequestBody PaymentRequest request) {
        try {
            PaymentMode mode = request.paymentMode != null ? 
                    PaymentMode.valueOf(request.paymentMode) : PaymentMode.BANK_TRANSFER;
            
            return ResponseEntity.ok(payrollService.markAsPaid(
                    id, 
                    mode,
                    request.transactionReference,
                    request.bankName,
                    request.bankAccount,
                    request.upiId,
                    request.chequeNumber,
                    request.paidBy
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Process bulk payment for a month
     */
    @PostMapping("/pay-all")
    public ResponseEntity<List<Payroll>> processMonthlyPayment(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestBody PaymentRequest request) {
        PaymentMode mode = request.paymentMode != null ? 
                PaymentMode.valueOf(request.paymentMode) : PaymentMode.BANK_TRANSFER;
        
        return ResponseEntity.ok(payrollService.processMonthlyPayment(
                resolveOrgId(orgId), year, month, mode, request.paidBy));
    }

    // ============ DELETE ============

    /**
     * Delete single payroll (only if DRAFT)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayroll(@PathVariable Long id) {
        try {
            payrollService.deletePayroll(id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete all payrolls for a month (only if all are DRAFT)
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteMonthlyPayroll(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        String resolvedOrgId = resolveOrgId(orgId);
        log.info("🗑️ Delete request for payroll: orgId={}, year={}, month={}", resolvedOrgId, year, month);
        try {
            int deletedCount = payrollService.deleteMonthlyPayroll(resolvedOrgId, year, month);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deleted", deletedCount);
            response.put("message", deletedCount + " payroll record(s) deleted");
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warn("⚠️ Cannot delete payrolls: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============ DTO ============

    public static class PaymentRequest {
        public String paymentMode;        // BANK_TRANSFER, UPI, CASH, CHEQUE, NEFT, RTGS, IMPS
        public String transactionReference;
        public String bankName;
        public String bankAccount;
        public String upiId;
        public String chequeNumber;
        public String paidBy;
    }
}
