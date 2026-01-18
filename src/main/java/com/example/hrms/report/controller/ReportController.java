package com.example.hrms.report.controller;

import com.example.hrms.report.service.ReportService;
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

    // =========== ATTENDANCE REPORTS ===========

    @GetMapping("/attendance/monthly")
    public List<Map<String, Object>> getMonthlyAttendanceReport(
            @RequestParam String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getMonthlyAttendanceReport(orgId, year, month);
    }

    @GetMapping("/attendance/daily")
    public List<Map<String, Object>> getDailyAttendanceReport(
            @RequestParam String orgId,
            @RequestParam String date) {
        return reportService.getDailyAttendanceReport(orgId, LocalDate.parse(date));
    }

    // =========== PAYROLL REPORTS ===========

    @GetMapping("/payroll/salary-sheet")
    public Map<String, Object> getSalarySheet(
            @RequestParam String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getMonthlySalarySheet(orgId, year, month);
    }

    @GetMapping("/payroll/epf")
    public List<Map<String, Object>> getEpfReport(
            @RequestParam String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getEpfReport(orgId, year, month);
    }

    @GetMapping("/payroll/esic")
    public List<Map<String, Object>> getEsicReport(
            @RequestParam String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getEsicReport(orgId, year, month);
    }

    @GetMapping("/payroll/payslip")
    public org.springframework.http.ResponseEntity<Map<String, Object>> getPayslip(
            @RequestParam String orgId,
            @RequestParam String empId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        Map<String, Object> payslip = reportService.getPayslip(orgId, empId, year, month);
        if (payslip == null) {
            return org.springframework.http.ResponseEntity.status(404).body(Map.of(
                "error", "Payslip not found",
                "message", "No payroll record found for employee " + empId + " for " + month + "/" + year + ". Please generate payroll first."
            ));
        }
        return org.springframework.http.ResponseEntity.ok(payslip);
    }

    // =========== LOAN REPORTS ===========

    @GetMapping("/loans/active")
    public List<Map<String, Object>> getActiveLoansReport(@RequestParam String orgId) {
        return reportService.getActiveLoansReport(orgId);
    }

    @GetMapping("/loans/deductions")
    public List<Map<String, Object>> getLoanDeductionReport(
            @RequestParam String orgId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return reportService.getLoanDeductionReport(orgId, year, month);
    }
}
