package com.example.hrms.admin.service;

import com.example.hrms.attendance.repo.*;
import com.example.hrms.loan.repo.LoanRepository;
import com.example.hrms.loan.repo.LoanRepaymentRepository;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to handle data reset/cleanup operations before re-import.
 * Ensures all associated data (attendance, payroll, loans, shift assignments) 
 * are properly cleared when re-importing for a month.
 */
@Service
@Slf4j
public class DataResetService {

    private final AttendanceDayRepository attendanceDayRepo;
    private final AttendancePunchRepository attendancePunchRepo;
    private final ImportBatchRepository importBatchRepo;
    private final PayrollRepository payrollRepo;
    private final LoanRepository loanRepo;
    private final LoanRepaymentRepository loanRepaymentRepo;
    private final EmployeeShiftAssignmentRepository shiftAssignmentRepo;

    public DataResetService(
            AttendanceDayRepository attendanceDayRepo,
            AttendancePunchRepository attendancePunchRepo,
            ImportBatchRepository importBatchRepo,
            PayrollRepository payrollRepo,
            LoanRepository loanRepo,
            LoanRepaymentRepository loanRepaymentRepo,
            EmployeeShiftAssignmentRepository shiftAssignmentRepo) {
        this.attendanceDayRepo = attendanceDayRepo;
        this.attendancePunchRepo = attendancePunchRepo;
        this.importBatchRepo = importBatchRepo;
        this.payrollRepo = payrollRepo;
        this.loanRepo = loanRepo;
        this.loanRepaymentRepo = loanRepaymentRepo;
        this.shiftAssignmentRepo = shiftAssignmentRepo;
    }

    /**
     * Reset all data for a specific month before re-import.
     * This clears:
     * - Attendance data (punches and daily records)
     * - Payroll data
     * - Loan deduction associations (makes loans available for re-deduction)
     * - Import batch records
     * 
     * Does NOT clear:
     * - Employee master data
     * - Shift assignments (these are permanent)
     * - Biometric device associations
     * - Loan principal data (only clears payroll associations)
     */
    @Transactional
    public Map<String, Object> resetMonthData(String tenantId, int year, int month) {
        Map<String, Object> result = new HashMap<>();
        log.info("Starting data reset for tenant={}, year={}, month={}", tenantId, year, month);

        try {
            // Convert tenantId to orgId for entities that use orgId
            Long orgId = resolveOrgId(tenantId);
            
            // 1. Delete payroll for the month
            int payrollDeleted = payrollRepo.deleteByTenantIdAndYearAndMonth(tenantId, year, month);
            result.put("payrollDeleted", payrollDeleted);
            log.info("Deleted {} payroll records", payrollDeleted);

            // 2. Clear loan deduction associations for this month
            // This makes one-time loans available for re-deduction
            int loansReset = resetLoanAssociationsForMonth(tenantId, year, month);
            result.put("loansReset", loansReset);
            log.info("Reset {} loan associations", loansReset);

            // 3. Delete attendance day records for the month
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            int attendanceDaysDeleted = attendanceDayRepo.deleteByTenantIdAndWorkDateBetween(tenantId, startDate, endDate);
            result.put("attendanceDaysDeleted", attendanceDaysDeleted);
            log.info("Deleted {} attendance day records", attendanceDaysDeleted);

            // 4. Delete attendance punches for the month (uses orgId and Instant)
            if (orgId != null) {
                Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant endInstant = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                int punchesDeleted = attendancePunchRepo.deleteByOrgIdAndPunchTsUtcBetween(orgId, startInstant, endInstant);
                result.put("punchesDeleted", punchesDeleted);
                log.info("Deleted {} punch records", punchesDeleted);
                
                // 5. Delete import batch records for the month (uses orgId)
                int batchesDeleted = importBatchRepo.deleteByOrgIdAndYearAndMonth(orgId, year, month);
                result.put("batchesDeleted", batchesDeleted);
                log.info("Deleted {} import batch records", batchesDeleted);
            } else {
                result.put("punchesDeleted", 0);
                result.put("batchesDeleted", 0);
                log.warn("Could not parse orgId from tenantId, skipping punch and batch deletion");
            }

            result.put("success", true);
            result.put("message", String.format("Successfully reset data for %d/%d", month, year));
            
        } catch (Exception e) {
            log.error("Error resetting month data: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
    
    /**
     * Resolve orgId from tenantId.
     * First tries to parse directly as a number, then looks up from existing attendance data.
     * This handles both numeric tenantIds (e.g., "123") and string-based ones (e.g., "PASA").
     */
    private Long resolveOrgId(String tenantId) {
        if (tenantId == null) return null;
        
        // Try parsing directly as number
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            // Not a number - look up from attendance_day table
        }
        
        // Try extracting number from "org_123" format
        if (tenantId.startsWith("org_")) {
            try {
                return Long.parseLong(tenantId.substring(4));
            } catch (NumberFormatException e) {
                // Not in org_123 format
            }
        }
        
        // Look up orgId from existing attendance data for this tenant
        var orgIds = attendanceDayRepo.findDistinctOrgIdByTenantId(tenantId);
        if (!orgIds.isEmpty()) {
            Long orgId = orgIds.get(0);
            log.info("Resolved orgId={} from tenantId={}", orgId, tenantId);
            return orgId;
        }
        
        log.warn("Could not resolve orgId for tenantId={}", tenantId);
        return null;
    }

    /**
     * Reset loan associations for a specific month.
     * This reverses any one-time or flexible loan deductions that were marked for this month.
     */
    private int resetLoanAssociationsForMonth(String tenantId, int year, int month) {
        var loans = loanRepo.findByDeductedInMonthYear(tenantId, month, year);
        int count = 0;
        
        for (var loan : loans) {
            // Determine the amount to reverse
            java.math.BigDecimal amountToReverse;
            if (loan.getDeductedAmount() != null) {
                amountToReverse = loan.getDeductedAmount();
            } else {
                amountToReverse = loan.getEmiAmount();
            }
            
            // Reverse the deduction
            if (amountToReverse != null) {
                loan.setTotalPaid(loan.getTotalPaid().subtract(amountToReverse));
                loan.setOutstandingBalance(loan.getOutstandingBalance().add(amountToReverse));
            }
            loan.setEmisPaid(0);
            loan.setStatus(com.example.hrms.loan.domain.enums.LoanStatus.ACTIVE);
            loan.setClosedDate(null);
            loan.clearDeductionAssociation();
            loanRepo.save(loan);
            count++;
        }
        
        return count;
    }

    /**
     * Get summary of data that will be affected by reset.
     * Use this for confirmation before actual reset.
     */
    public Map<String, Object> getResetPreview(String tenantId, int year, int month) {
        Map<String, Object> preview = new HashMap<>();
        
        java.time.LocalDate startDate = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        // Count records that will be affected
        long payrollCount = payrollRepo.countByOrgIdAndYearAndMonth(tenantId, year, month);
        long attendanceDayCount = attendanceDayRepo.countByTenantIdAndWorkDateBetween(tenantId, startDate, endDate);
        long loanAssociationCount = loanRepo.findByDeductedInMonthYear(tenantId, month, year).size();
        
        preview.put("payrollRecords", payrollCount);
        preview.put("attendanceRecords", attendanceDayCount);
        preview.put("loanAssociations", loanAssociationCount);
        preview.put("month", month);
        preview.put("year", year);
        preview.put("warning", "This action cannot be undone. All data for this month will be deleted.");
        
        return preview;
    }
}
