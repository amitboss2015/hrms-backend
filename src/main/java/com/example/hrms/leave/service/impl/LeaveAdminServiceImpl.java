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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class LeaveAdminServiceImpl implements LeaveAdminService {

    private final EmployeeLeaveRepository leaveRepo;
    private final LeaveTypeRepository typeRepo;
    private final LeaveLedgerRepository ledgerRepo;
    private final EmployeeLeaveAllocationRepository allocationRepo;
    private final LeaveLedgerServiceImpl ledgerService;

    private static final List<LeaveStatus> CONSUMED_STATUSES = List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING);

    public LeaveAdminServiceImpl(EmployeeLeaveRepository leaveRepo,
                                  LeaveTypeRepository typeRepo,
                                  LeaveLedgerRepository ledgerRepo,
                                  EmployeeLeaveAllocationRepository allocationRepo,
                                  LeaveLedgerServiceImpl ledgerService) {
        this.leaveRepo = leaveRepo;
        this.typeRepo = typeRepo;
        this.ledgerRepo = ledgerRepo;
        this.allocationRepo = allocationRepo;
        this.ledgerService = ledgerService;
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
        el.setStatus(LeaveStatus.APPROVED); // Auto-approve for admin marking
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

        // Update the ledger/allocation balances
        if (willConsumeMonthly.compareTo(BigDecimal.ZERO) > 0) {
            updateMonthlyLedger(req.orgId(), req.empId(), req.leaveTypeId(), year, month, willConsumeMonthly);
        }
        if (willConsumeAnnual.compareTo(BigDecimal.ZERO) > 0) {
            updateAnnualAllocation(req.orgId(), req.empId(), req.leaveTypeId(), year, willConsumeAnnual);
        }

        return saved;
    }

    @Override
    public void cancel(Long leaveId, String reason) {
        EmployeeLeave leave = leaveRepo.findById(leaveId).orElse(null);
        if (leave == null) return;

        // Reverse the balance consumption
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();

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
        return rows.stream().map(el -> new EmployeeLeaveRow(
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
                                el.getLeaveType().getName())
        )).toList();
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
}
