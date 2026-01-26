package com.example.hrms.leave.service.impl;

import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.domain.EmployeeLeaveAllocation;
import com.example.hrms.leave.domain.LeaveLedger;
import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.domain.enums.*;
import com.example.hrms.leave.dto.EmployeeLeaveRow;
import com.example.hrms.leave.dto.LeaveTypeSummary;
import com.example.hrms.leave.dto.MarkLeavePreview;
import com.example.hrms.leave.dto.MarkLeaveRequest;
import com.example.hrms.leave.repo.EmployeeLeaveAllocationRepository;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.leave.repo.LeaveLedgerRepository;
import com.example.hrms.leave.repo.LeaveTypeRepository;
import com.example.hrms.leave.service.LeaveAdminService;
import com.example.hrms.payroll.domain.enums.PayrollStatus;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class LeaveAdminServiceImpl implements LeaveAdminService {

    private final EmployeeLeaveRepository leaveRepo;
    private final LeaveTypeRepository typeRepo;
    private final LeaveLedgerRepository ledgerRepo;
    private final EmployeeLeaveAllocationRepository allocationRepo;
    private final LeaveLedgerServiceImpl ledgerService;
    private final PayrollRepository payrollRepo;

    private static final List<LeaveStatus> CONSUMED_STATUSES = List.of(LeaveStatus.APPROVED); // Only APPROVED leaves consume balance

    public LeaveAdminServiceImpl(EmployeeLeaveRepository leaveRepo,
                                  LeaveTypeRepository typeRepo,
                                  LeaveLedgerRepository ledgerRepo,
                                  EmployeeLeaveAllocationRepository allocationRepo,
                                  LeaveLedgerServiceImpl ledgerService,
                                  PayrollRepository payrollRepo) {
        this.leaveRepo = leaveRepo;
        this.typeRepo = typeRepo;
        this.ledgerRepo = ledgerRepo;
        this.allocationRepo = allocationRepo;
        this.ledgerService = ledgerService;
        this.payrollRepo = payrollRepo;
    }

    @Override
    public MarkLeavePreview preview(MarkLeaveRequest req) {
        LeaveType lt = typeRepo.findById(req.leaveTypeId()).orElseThrow(
                () -> new IllegalArgumentException("Leave type not found: " + req.leaveTypeId()));

        int year = req.startDate().getYear();
        int month = req.startDate().getMonthValue();

        // Get current balances
        LeaveLedgerServiceImpl.BalanceResult balance = ledgerService.getAvailableBalance(
                req.orgId(), req.empId(), req.leaveTypeId(), year, month);

        BigDecimal totalDays = req.totalDays() != null ? req.totalDays() : BigDecimal.ONE;
        BigDecimal monthlyAvailable = balance.monthlyBalance();
        BigDecimal annualAvailable = balance.annualBalance();

        // Calculate consumption based on consume order
        ConsumeOrder order = lt.getConsumeOrder() != null ? lt.getConsumeOrder() : ConsumeOrder.MONTHLY_THEN_ANNUAL;
        
        BigDecimal willConsumeMonthly = BigDecimal.ZERO;
        BigDecimal willConsumeAnnual = BigDecimal.ZERO;

        if (order == ConsumeOrder.MONTHLY_THEN_ANNUAL) {
            willConsumeMonthly = totalDays.min(monthlyAvailable);
            BigDecimal remaining = totalDays.subtract(willConsumeMonthly);
            willConsumeAnnual = remaining.min(annualAvailable);
        } else {
            willConsumeAnnual = totalDays.min(annualAvailable);
            BigDecimal remaining = totalDays.subtract(willConsumeAnnual);
            willConsumeMonthly = remaining.min(monthlyAvailable);
        }

        BigDecimal totalConsumed = willConsumeMonthly.add(willConsumeAnnual);
        BigDecimal unpaidDays = totalDays.subtract(totalConsumed).max(BigDecimal.ZERO);
        boolean wouldConvertToUnpaid = unpaidDays.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal monthlyBalanceAfter = monthlyAvailable.subtract(willConsumeMonthly);
        BigDecimal annualBalanceAfter = annualAvailable.subtract(willConsumeAnnual);

        return new MarkLeavePreview(
                willConsumeMonthly,
                willConsumeAnnual,
                monthlyBalanceAfter,
                annualBalanceAfter,
                wouldConvertToUnpaid,
                unpaidDays
        );
    }

    @Override
    public EmployeeLeave mark(MarkLeaveRequest req) {
        LeaveType lt = typeRepo.findById(req.leaveTypeId()).orElseThrow(
                () -> new IllegalArgumentException("Leave type not found: " + req.leaveTypeId()));

        int year = req.startDate().getYear();
        int month = req.startDate().getMonthValue();
        
        // Check if payroll is already approved for this month - if so, prevent modification
        String tenantId = req.orgId(); // Use orgId as tenantId
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, req.empId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                throw new IllegalStateException(
                    String.format("Cannot modify leave for employee %s in %s %d because payroll is already %s. " +
                                "Please delete or regenerate the payroll first.",
                                req.empId(), java.time.Month.of(month), year, status));
            }
        }

        // Get current balances
        LeaveLedgerServiceImpl.BalanceResult balance = ledgerService.getAvailableBalance(
                req.orgId(), req.empId(), req.leaveTypeId(), year, month);

        BigDecimal totalDays = req.totalDays() != null ? req.totalDays() : BigDecimal.ONE;
        BigDecimal monthlyAvailable = balance.monthlyBalance();
        BigDecimal annualAvailable = balance.annualBalance();

        // Calculate consumption
        ConsumeOrder order = lt.getConsumeOrder() != null ? lt.getConsumeOrder() : ConsumeOrder.MONTHLY_THEN_ANNUAL;
        
        BigDecimal willConsumeMonthly = BigDecimal.ZERO;
        BigDecimal willConsumeAnnual = BigDecimal.ZERO;

        if (order == ConsumeOrder.MONTHLY_THEN_ANNUAL) {
            willConsumeMonthly = totalDays.min(monthlyAvailable);
            BigDecimal remaining = totalDays.subtract(willConsumeMonthly);
            willConsumeAnnual = remaining.min(annualAvailable);
        } else {
            willConsumeAnnual = totalDays.min(annualAvailable);
            BigDecimal remaining = totalDays.subtract(willConsumeAnnual);
            willConsumeMonthly = remaining.min(monthlyAvailable);
        }

        BigDecimal totalConsumed = willConsumeMonthly.add(willConsumeAnnual);
        BigDecimal unpaidDays = totalDays.subtract(totalConsumed).max(BigDecimal.ZERO);

        // Determine consumed from
        ConsumedFrom consumedFrom;
        if (willConsumeMonthly.compareTo(BigDecimal.ZERO) > 0 && willConsumeAnnual.compareTo(BigDecimal.ZERO) > 0) {
            consumedFrom = ConsumedFrom.MIXED;
        } else if (willConsumeAnnual.compareTo(BigDecimal.ZERO) > 0) {
            consumedFrom = ConsumedFrom.ANNUAL;
        } else {
            consumedFrom = ConsumedFrom.MONTHLY;
        }

        // Create the leave record
        EmployeeLeave el = new EmployeeLeave();
        el.setOrgId(req.orgId());
        el.setEmpId(req.empId());
        el.setLeaveType(lt);
        el.setStartDate(req.startDate());
        el.setEndDate(req.endDate());
        el.setDurationKind(req.durationKind() != null ? req.durationKind() : DurationKind.FULL_DAY);
        el.setTotalDays(totalDays);
        el.setRemarks(req.remarks());
        el.setStatus(LeaveStatus.REVIEW); // New leaves start in REVIEW state, require admin approval
        el.setPayable(Boolean.TRUE.equals(lt.getIsPaid()) && unpaidDays.compareTo(BigDecimal.ZERO) == 0);
        el.setConsumesBalance(true);
        el.setConsumedFrom(consumedFrom);
        el.setConsumedFromYear(year);

        // Store consumption breakdown as JSON
        String breakupJson = String.format(
                "{\"monthly\":%.2f,\"annual\":%.2f,\"unpaid\":%.2f}",
                willConsumeMonthly.doubleValue(),
                willConsumeAnnual.doubleValue(),
                unpaidDays.doubleValue()
        );
        el.setConsumptionBreakupJson(breakupJson);

        EmployeeLeave saved = leaveRepo.save(el);

        // DO NOT consume balance here - balance consumption happens only when leave is APPROVED
        // This allows admin to review and approve/reject before balance is consumed

        return saved;
    }

    @Override
    public void cancel(Long leaveId, String reason) {
        EmployeeLeave leave = leaveRepo.findById(leaveId).orElse(null);
        if (leave == null) return;

        // Check if payroll is already approved for this month - if so, prevent deletion
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String tenantId = leave.getTenantId() != null ? leave.getTenantId() : leave.getOrgId();
        
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, leave.getEmpId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                throw new IllegalStateException(
                    String.format("Cannot delete leave for employee %s in %s %d because payroll is already %s. " +
                                "Please delete or regenerate the payroll first.",
                                leave.getEmpId(), java.time.Month.of(month), year, status));
            }
        }

        // Reverse the balance consumption

        if (leave.getConsumesBalance() && leave.getConsumedFrom() != null) {
            // Parse the consumption breakdown
            BigDecimal monthlyUsed = BigDecimal.ZERO;
            BigDecimal annualUsed = BigDecimal.ZERO;

            if (leave.getConsumptionBreakupJson() != null) {
                try {
                    String json = leave.getConsumptionBreakupJson();
                    if (json.contains("monthly")) {
                        String monthlyStr = json.split("\"monthly\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                        monthlyUsed = new BigDecimal(monthlyStr);
                    }
                    if (json.contains("annual")) {
                        String annualStr = json.split("\"annual\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                        annualUsed = new BigDecimal(annualStr);
                    }
                } catch (Exception e) {
                    // Fallback: use totalDays
                    if (leave.getConsumedFrom() == ConsumedFrom.MONTHLY) {
                        monthlyUsed = leave.getTotalDays();
                    } else if (leave.getConsumedFrom() == ConsumedFrom.ANNUAL) {
                        annualUsed = leave.getTotalDays();
                    } else {
                        monthlyUsed = leave.getTotalDays().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                        annualUsed = leave.getTotalDays().subtract(monthlyUsed);
                    }
                }
            } else {
                if (leave.getConsumedFrom() == ConsumedFrom.MONTHLY) {
                    monthlyUsed = leave.getTotalDays();
                } else if (leave.getConsumedFrom() == ConsumedFrom.ANNUAL) {
                    annualUsed = leave.getTotalDays();
                }
            }

            // Reverse monthly consumption
            if (monthlyUsed.compareTo(BigDecimal.ZERO) > 0) {
                reverseMonthlyLedger(leave.getOrgId(), leave.getEmpId(), 
                        leave.getLeaveType().getId(), year, month, monthlyUsed);
            }

            // Reverse annual consumption
            if (annualUsed.compareTo(BigDecimal.ZERO) > 0) {
                reverseAnnualAllocation(leave.getOrgId(), leave.getEmpId(), 
                        leave.getLeaveType().getId(), year, annualUsed);
            }
        }

        leaveRepo.deleteById(leaveId);
    }

    @Override
    public List<EmployeeLeave> listLeaves(String empId, String orgId,
                                           Integer year, Integer month,
                                           String from, String to) {
        // Simple implementation - returns all leaves for the employee
        return leaveRepo.findByOrgIdAndEmpIdOrderByStartDateDesc(orgId, empId);
    }

    @Override
    public List<EmployeeLeaveRow> listLeaves(String empId, String orgId) {
        List<EmployeeLeave> rows = leaveRepo.findByOrgIdAndEmpIdOrderByStartDateDesc(orgId, empId);
        return rows.stream().map(el -> {
            // Check if deletion/modification is allowed based on payroll status
            boolean canDelete = true;
            boolean canModify = true;
            
            int year = el.getStartDate().getYear();
            int month = el.getStartDate().getMonthValue();
            String tenantId = el.getTenantId() != null ? el.getTenantId() : el.getOrgId();
            
            var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, el.getEmpId(), year, month);
            if (existingPayroll.isPresent()) {
                PayrollStatus status = existingPayroll.get().getStatus();
                if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                    canDelete = false;
                    canModify = false;
                }
            }
            
            return new EmployeeLeaveRow(
                    el.getId(),
                    el.getDurationKind().name(),
                    el.getStartDate().toString(),
                    el.getEndDate().toString(),
                    el.getStatus().name(),
                    el.getRemarks(),
                    el.getTotalDays().toPlainString(),
                    el.getLeaveType() == null ? null :
                            new LeaveTypeSummary(el.getLeaveType().getId(), 
                                    el.getLeaveType().getCode(), 
                                    el.getLeaveType().getName()),
                    canDelete,
                    canModify
            );
        }).toList();
    }

    // ============ Helper Methods ============

    private void updateMonthlyLedger(String orgId, String empId, Long leaveTypeId, 
                                      int year, int month, BigDecimal days) {
        LeaveLedger ledger = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                orgId, empId, leaveTypeId, year, month).orElse(null);
        
        if (ledger == null) {
            // Trigger ledger creation
            ledgerService.getAvailableBalance(orgId, empId, leaveTypeId, year, month);
            ledger = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                    orgId, empId, leaveTypeId, year, month).orElse(null);
        }

        if (ledger != null) {
            ledger.setUsedFromMonthly(ledger.getUsedFromMonthly().add(days));
            BigDecimal closing = ledger.getOpeningMonthly()
                    .add(ledger.getAccruedMonthly())
                    .subtract(ledger.getUsedFromMonthly())
                    .subtract(ledger.getMovedToAnnual())
                    .subtract(ledger.getExpiredMonthly())
                    .max(BigDecimal.ZERO);
            ledger.setClosingMonthly(closing);
            ledgerRepo.save(ledger);
        }
    }

    private void updateAnnualAllocation(String orgId, String empId, Long leaveTypeId, 
                                         int year, BigDecimal days) {
        EmployeeLeaveAllocation alloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                orgId, empId, leaveTypeId, year).orElse(null);

        if (alloc == null) {
            // Trigger allocation creation
            ledgerService.getAvailableBalance(orgId, empId, leaveTypeId, year, 1);
            alloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                    orgId, empId, leaveTypeId, year).orElse(null);
        }

        if (alloc != null) {
            alloc.setUsedFromAnnual(alloc.getUsedFromAnnual().add(days));
            BigDecimal closing = alloc.getOpeningAnnual()
                    .add(alloc.getAccruedAnnual())
                    .subtract(alloc.getUsedFromAnnual())
                    .subtract(alloc.getCarriedForwardOut())
                    .subtract(alloc.getExpiredAnnual())
                    .max(BigDecimal.ZERO);
            alloc.setClosingAnnual(closing);
            allocationRepo.save(alloc);
        }
    }

    private void reverseMonthlyLedger(String orgId, String empId, Long leaveTypeId, 
                                       int year, int month, BigDecimal days) {
        LeaveLedger ledger = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                orgId, empId, leaveTypeId, year, month).orElse(null);

        if (ledger != null) {
            ledger.setUsedFromMonthly(ledger.getUsedFromMonthly().subtract(days).max(BigDecimal.ZERO));
            BigDecimal closing = ledger.getOpeningMonthly()
                    .add(ledger.getAccruedMonthly())
                    .subtract(ledger.getUsedFromMonthly())
                    .subtract(ledger.getMovedToAnnual())
                    .subtract(ledger.getExpiredMonthly())
                    .max(BigDecimal.ZERO);
            ledger.setClosingMonthly(closing);
            ledgerRepo.save(ledger);
        }
    }

    private void reverseAnnualAllocation(String orgId, String empId, Long leaveTypeId, 
                                          int year, BigDecimal days) {
        EmployeeLeaveAllocation alloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                orgId, empId, leaveTypeId, year).orElse(null);

        if (alloc != null) {
            alloc.setUsedFromAnnual(alloc.getUsedFromAnnual().subtract(days).max(BigDecimal.ZERO));
            BigDecimal closing = alloc.getOpeningAnnual()
                    .add(alloc.getAccruedAnnual())
                    .subtract(alloc.getUsedFromAnnual())
                    .subtract(alloc.getCarriedForwardOut())
                    .subtract(alloc.getExpiredAnnual())
                    .max(BigDecimal.ZERO);
            alloc.setClosingAnnual(closing);
            allocationRepo.save(alloc);
        }
    }

    @Override
    public EmployeeLeave approveLeave(Long leaveId, String remarks) {
        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found: " + leaveId));
        
        if (leave.getStatus() != LeaveStatus.REVIEW && leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Only leaves in REVIEW or PENDING status can be approved. Current status: " + leave.getStatus());
        }
        
        // Check if payroll is already approved for this month - if so, prevent approval
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String tenantId = leave.getTenantId() != null ? leave.getTenantId() : leave.getOrgId();
        
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, leave.getEmpId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                throw new IllegalStateException(
                    String.format("Cannot approve leave for employee %s in %s %d because payroll is already %s. " +
                                "Please delete or regenerate the payroll first.",
                                leave.getEmpId(), java.time.Month.of(month), year, status));
            }
        }
        
        // Update status to APPROVED
        leave.setStatus(LeaveStatus.APPROVED);
        if (remarks != null && !remarks.trim().isEmpty()) {
            String existingRemarks = leave.getRemarks();
            leave.setRemarks(existingRemarks != null ? existingRemarks + " | Approved: " + remarks : "Approved: " + remarks);
        }
        
        // Now that it's approved, consume the balance
        if (leave.getConsumesBalance() && leave.getConsumedFrom() != null) {
            
            // Parse consumption breakdown from JSON
            BigDecimal monthlyUsed = BigDecimal.ZERO;
            BigDecimal annualUsed = BigDecimal.ZERO;
            
            if (leave.getConsumptionBreakupJson() != null) {
                try {
                    String json = leave.getConsumptionBreakupJson();
                    if (json.contains("monthly")) {
                        String monthlyStr = json.split("\"monthly\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                        monthlyUsed = new BigDecimal(monthlyStr);
                    }
                    if (json.contains("annual")) {
                        String annualStr = json.split("\"annual\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                        annualUsed = new BigDecimal(annualStr);
                    }
                } catch (Exception e) {
                    // Fallback: use totalDays based on consumedFrom
                    if (leave.getConsumedFrom() == ConsumedFrom.MONTHLY) {
                        monthlyUsed = leave.getTotalDays();
                    } else if (leave.getConsumedFrom() == ConsumedFrom.ANNUAL) {
                        annualUsed = leave.getTotalDays();
                    } else {
                        monthlyUsed = leave.getTotalDays().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                        annualUsed = leave.getTotalDays().subtract(monthlyUsed);
                    }
                }
            }
            
            // Consume monthly balance
            if (monthlyUsed.compareTo(BigDecimal.ZERO) > 0) {
                updateMonthlyLedger(leave.getOrgId(), leave.getEmpId(), 
                        leave.getLeaveType().getId(), year, month, monthlyUsed);
            }
            
            // Consume annual balance
            if (annualUsed.compareTo(BigDecimal.ZERO) > 0) {
                updateAnnualAllocation(leave.getOrgId(), leave.getEmpId(), 
                        leave.getLeaveType().getId(), year, annualUsed);
            }
        }
        
        return leaveRepo.save(leave);
    }

    @Override
    public EmployeeLeave rejectLeave(Long leaveId, String remarks) {
        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found: " + leaveId));
        
        if (leave.getStatus() != LeaveStatus.REVIEW && leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Only leaves in REVIEW or PENDING status can be rejected. Current status: " + leave.getStatus());
        }
        
        // Check if payroll is already approved for this month - if so, prevent rejection of already approved leaves
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String tenantId = leave.getTenantId() != null ? leave.getTenantId() : leave.getOrgId();
        
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, leave.getEmpId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                // Rejection is allowed even if payroll is approved, but warn if leave was already approved
                if (leave.getStatus() == LeaveStatus.APPROVED) {
                    throw new IllegalStateException(
                        String.format("Cannot reject already approved leave for employee %s in %s %d because payroll is already %s. " +
                                    "Please delete or regenerate the payroll first.",
                                    leave.getEmpId(), java.time.Month.of(month), year, status));
                }
            }
        }
        
        // Update status to REJECTED
        leave.setStatus(LeaveStatus.REJECTED);
        if (remarks != null && !remarks.trim().isEmpty()) {
            String existingRemarks = leave.getRemarks();
            leave.setRemarks(existingRemarks != null ? existingRemarks + " | Rejected: " + remarks : "Rejected: " + remarks);
        }
        
        // No need to reverse balance since REVIEW leaves don't consume balance
        
        return leaveRepo.save(leave);
    }

    @Override
    public Map<String, Boolean> checkLeaveActions(Long leaveId) {
        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found: " + leaveId));
        
        boolean canDelete = true;
        boolean canModify = true;
        
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String tenantId = leave.getTenantId() != null ? leave.getTenantId() : leave.getOrgId();
        
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, leave.getEmpId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                canDelete = false;
                canModify = false;
            }
        }
        
        return Map.of("canDelete", canDelete, "canModify", canModify);
    }

    @Override
    public EmployeeLeave updatePayableFlag(Long leaveId, Boolean payable) {
        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found: " + leaveId));
        
        // Check if payroll is already approved for this month - if so, prevent modification
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String tenantId = leave.getTenantId() != null ? leave.getTenantId() : leave.getOrgId();
        
        var existingPayroll = payrollRepo.findByTenantIdAndEmpIdAndYearAndMonth(tenantId, leave.getEmpId(), year, month);
        if (existingPayroll.isPresent()) {
            PayrollStatus status = existingPayroll.get().getStatus();
            if (status == PayrollStatus.APPROVED || status == PayrollStatus.PAID) {
                throw new IllegalStateException(
                    String.format("Cannot modify leave payable flag for employee %s in %s %d because payroll is already %s. " +
                                "Please delete or regenerate the payroll first.",
                                leave.getEmpId(), java.time.Month.of(month), year, status));
            }
        }
        
        // Update payable flag
        leave.setPayable(payable != null ? payable : Boolean.TRUE);
        
        return leaveRepo.save(leave);
    }

    @Override
    public Map<String, Object> deleteAllLeaves(String orgId, String empId) {
        List<EmployeeLeave> leavesToDelete;
        
        if (empId != null && !empId.trim().isEmpty()) {
            // Delete leaves for specific employee
            leavesToDelete = leaveRepo.findByOrgIdAndEmpIdOrderByStartDateDesc(orgId, empId);
        } else {
            // Delete all leaves for the organization
            String tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : orgId;
            // Use findByTenantIdAndEmpIdOrderByStartDateDesc with all employees or find all
            // Since we don't have a findByTenantId method, we'll need to get all leaves and filter
            List<EmployeeLeave> allLeaves = leaveRepo.findAll();
            leavesToDelete = allLeaves.stream()
                    .filter(l -> (l.getTenantId() != null && l.getTenantId().equals(tenantId)) || 
                                (l.getTenantId() == null && l.getOrgId().equals(orgId)))
                    .toList();
        }
        
        int deletedCount = 0;
        for (EmployeeLeave leave : leavesToDelete) {
            try {
                // Reverse balance consumption if needed
                if (leave.getConsumesBalance() && leave.getConsumedFrom() != null && leave.getStatus() == LeaveStatus.APPROVED) {
                    int year = leave.getStartDate().getYear();
                    int month = leave.getStartDate().getMonthValue();
                    
                    BigDecimal monthlyUsed = BigDecimal.ZERO;
                    BigDecimal annualUsed = BigDecimal.ZERO;
                    
                    if (leave.getConsumptionBreakupJson() != null) {
                        try {
                            String json = leave.getConsumptionBreakupJson();
                            if (json.contains("monthly")) {
                                String monthlyStr = json.split("\"monthly\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                                monthlyUsed = new BigDecimal(monthlyStr);
                            }
                            if (json.contains("annual")) {
                                String annualStr = json.split("\"annual\":")[1].split(",")[0].replaceAll("[^0-9.]", "");
                                annualUsed = new BigDecimal(annualStr);
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                    
                    // Reverse consumption
                    if (monthlyUsed.compareTo(BigDecimal.ZERO) > 0) {
                        reverseMonthlyLedger(leave.getOrgId(), leave.getEmpId(), 
                                leave.getLeaveType().getId(), year, month, monthlyUsed);
                    }
                    if (annualUsed.compareTo(BigDecimal.ZERO) > 0) {
                        reverseAnnualAllocation(leave.getOrgId(), leave.getEmpId(), 
                                leave.getLeaveType().getId(), year, annualUsed);
                    }
                }
                
                leaveRepo.deleteById(leave.getId());
                deletedCount++;
            } catch (Exception e) {
                // Log error but continue with other leaves
                System.err.println("Error deleting leave " + leave.getId() + ": " + e.getMessage());
            }
        }
        
        return Map.of(
            "success", true,
            "deletedCount", deletedCount,
            "message", "Deleted " + deletedCount + " leave(s)"
        );
    }
}
