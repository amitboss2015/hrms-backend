package com.example.hrms.leave.controller;

import com.example.hrms.leave.dto.LedgerView;
import com.example.hrms.leave.service.LeaveLedgerService;
import com.example.hrms.leave.service.impl.LeaveLedgerServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/leave/balances")
@CrossOrigin(origins = "*")
public class LeaveBalanceController {

    private final LeaveLedgerService ledgerService;
    private final LeaveLedgerServiceImpl ledgerServiceImpl;

    public LeaveBalanceController(LeaveLedgerService ledgerService, LeaveLedgerServiceImpl ledgerServiceImpl) {
        this.ledgerService = ledgerService;
        this.ledgerServiceImpl = ledgerServiceImpl;
    }

    /**
     * Get leave balances for an employee for a specific year
     * GET /api/leave/balances/{empId}?orgId=ORG1&year=2025
     */
    @GetMapping("/{empId}")
    public Map<String, Object> getBalances(
            @RequestParam String orgId,
            @PathVariable String empId,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month) {
        
        if (month != null) {
            // Return a single month view under a key
            return Map.of("month", ledgerService.getMonthlyLedger(orgId, empId, null, year, month));
        }
        return ledgerService.getBalances(orgId, empId, year);
    }

    /**
     * Get leave balance for a specific leave type
     * GET /api/leave/balances/{empId}/type/{leaveTypeId}?orgId=ORG1&year=2025&month=1
     */
    @GetMapping("/{empId}/type/{leaveTypeId}")
    public Map<String, Object> getBalanceForType(
            @RequestParam String orgId,
            @PathVariable String empId,
            @PathVariable Long leaveTypeId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        LeaveLedgerServiceImpl.BalanceResult balance = ledgerServiceImpl.getAvailableBalance(
                orgId, empId, leaveTypeId, year, month);
        
        LedgerView monthlyView = ledgerService.getMonthlyLedger(orgId, empId, leaveTypeId, year, month);
        
        return Map.of(
                "empId", empId,
                "leaveTypeId", leaveTypeId,
                "year", year,
                "month", month,
                "monthlyBalance", balance.monthlyBalance(),
                "annualBalance", balance.annualBalance(),
                "totalAvailable", balance.totalBalance(),
                "monthlyLedger", monthlyView
        );
    }

    /**
     * Initialize allocations for an employee for a year (typically called at year start)
     * POST /api/leave/balances/{empId}/initialize?orgId=ORG1&year=2025&leaveTypeId=1
     */
    @PostMapping("/{empId}/initialize")
    public Map<String, Object> initializeAllocation(
            @RequestParam String orgId,
            @PathVariable String empId,
            @RequestParam Long leaveTypeId,
            @RequestParam Integer year) {
        
        ledgerServiceImpl.initializeAnnualAllocation(orgId, empId, leaveTypeId, year);
        
        return Map.of(
                "status", "SUCCESS",
                "message", "Allocation initialized for employee " + empId + " for year " + year
        );
    }

    /**
     * Trigger monthly accrual for an employee
     * POST /api/leave/balances/{empId}/accrue?orgId=ORG1&leaveTypeId=1&year=2025&month=1
     */
    @PostMapping("/{empId}/accrue")
    public Map<String, Object> accrueMonthly(
            @RequestParam String orgId,
            @PathVariable String empId,
            @RequestParam Long leaveTypeId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        
        ledgerService.accrueAtMonthStart(orgId, empId, leaveTypeId, year, month);
        
        return Map.of(
                "status", "SUCCESS",
                "message", "Accrual processed for " + year + "/" + month
        );
    }
}
