package com.example.hrms.report.controller;

import com.example.hrms.report.service.ReportService;
import com.example.hrms.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
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

    // =========== ATTENDANCE REPORTS ===========

    @GetMapping("/attendance/monthly")
    public List<Map<String, Object>> getMonthlyAttendanceReport(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getMonthlyAttendanceReport(resolveOrgId(orgId), year, month);
    }

    @GetMapping("/attendance/daily")
    public List<Map<String, Object>> getDailyAttendanceReport(
            @RequestParam(required = false) String orgId,
            @RequestParam String date) {
        return reportService.getDailyAttendanceReport(resolveOrgId(orgId), LocalDate.parse(date));
    }

    // =========== PAYROLL REPORTS ===========

    @GetMapping("/payroll/salary-sheet")
    public Map<String, Object> getSalarySheet(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getMonthlySalarySheet(resolveOrgId(orgId), year, month);
    }

    @GetMapping("/payroll/epf")
    public List<Map<String, Object>> getEpfReport(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getEpfReport(resolveOrgId(orgId), year, month);
    }

    @GetMapping("/payroll/esic")
    public List<Map<String, Object>> getEsicReport(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getEsicReport(resolveOrgId(orgId), year, month);
    }

    @GetMapping("/payroll/payslip")
    public ResponseEntity<Map<String, Object>> getPayslip(
            @RequestParam(required = false) String orgId,
            @RequestParam String empId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        Map<String, Object> payslip = reportService.getPayslip(resolveOrgId(orgId), empId, year, month);
        if (payslip == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Payslip not found",
                "message", "No payroll record found for employee " + empId + " for " + month + "/" + year + ". Please generate payroll first."
            ));
        }
        return ResponseEntity.ok(payslip);
    }

    // =========== LOAN REPORTS ===========

    @GetMapping("/loans/active")
    public List<Map<String, Object>> getActiveLoansReport(@RequestParam(required = false) String orgId) {
        return reportService.getActiveLoansReport(resolveOrgId(orgId));
    }

    @GetMapping("/loans/deductions")
    public List<Map<String, Object>> getLoanDeductionReport(
            @RequestParam(required = false) String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getLoanDeductionReport(resolveOrgId(orgId), year, month);
    }
}
