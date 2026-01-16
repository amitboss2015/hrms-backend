package com.example.hrms.leave.service.impl;

import com.example.hrms.leave.domain.EmployeeLeaveAllocation;
import com.example.hrms.leave.domain.LeaveLedger;
import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.domain.enums.*;
import com.example.hrms.leave.dto.LedgerView;
import com.example.hrms.leave.repo.EmployeeLeaveAllocationRepository;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.leave.repo.LeaveLedgerRepository;
import com.example.hrms.leave.repo.LeaveTypeRepository;
import com.example.hrms.leave.service.LeaveLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class LeaveLedgerServiceImpl implements LeaveLedgerService {

    private final LeaveLedgerRepository ledgerRepo;
    private final EmployeeLeaveAllocationRepository allocationRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final EmployeeLeaveRepository leaveRepo;

    private static final List<LeaveStatus> CONSUMED_STATUSES = List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING);

    public LeaveLedgerServiceImpl(LeaveLedgerRepository ledgerRepo,
                                   EmployeeLeaveAllocationRepository allocationRepo,
                                   LeaveTypeRepository leaveTypeRepo,
                                   EmployeeLeaveRepository leaveRepo) {
        this.ledgerRepo = ledgerRepo;
        this.allocationRepo = allocationRepo;
        this.leaveTypeRepo = leaveTypeRepo;
        this.leaveRepo = leaveRepo;
    }

    /**
     * Get the current available balance for an employee and leave type.
     * This computes: (Monthly Available) + (Annual Available)
     */
    public BalanceResult getAvailableBalance(String orgId, String empId, Long leaveTypeId, int year, int month) {
        LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElse(null);
        if (lt == null) {
            return new BalanceResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal monthlyBalance = BigDecimal.ZERO;
        BigDecimal annualBalance = BigDecimal.ZERO;

        // Get or create the monthly ledger
        LeaveLedger ledger = getOrCreateLedger(orgId, empId, leaveTypeId, year, month);
        monthlyBalance = ledger.getClosingMonthly();

        // Get or create the annual allocation
        EmployeeLeaveAllocation allocation = getOrCreateAllocation(orgId, empId, leaveTypeId, year);
        annualBalance = calculateAnnualAvailable(allocation);

        BigDecimal total = monthlyBalance.add(annualBalance);
        return new BalanceResult(monthlyBalance, annualBalance, total);
    }

    /**
     * Get monthly ledger view for a specific month
     */
    @Override
    public LedgerView getMonthlyLedger(String orgId, String empId, Long leaveTypeId, int year, int month) {
        if (leaveTypeId == null) {
            // Return empty if no leave type specified
            return new LedgerView(year, month, BigDecimal.ZERO, BigDecimal.ZERO, 
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, LockState.OPEN);
        }

        LeaveLedger ledger = getOrCreateLedger(orgId, empId, leaveTypeId, year, month);
        return new LedgerView(
            ledger.getLeaveYear(),
            ledger.getLeaveMonth(),
            ledger.getOpeningMonthly(),
            ledger.getAccruedMonthly(),
            ledger.getUsedFromMonthly(),
            ledger.getMovedToAnnual(),
            ledger.getExpiredMonthly(),
            ledger.getClosingMonthly(),
            ledger.getLockState()
        );
    }

    /**
     * Get comprehensive balance view for an employee for a year
     */
    @Override
    public Map<String, Object> getBalances(String orgId, String empId, int year) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orgId", orgId);
        result.put("empId", empId);
        result.put("year", year);

        List<LeaveType> leaveTypes = leaveTypeRepo.findByOrgIdAndActiveTrue(orgId);
        List<Map<String, Object>> balances = new ArrayList<>();

        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        for (LeaveType lt : leaveTypes) {
            Map<String, Object> typeBalance = new LinkedHashMap<>();
            typeBalance.put("leaveTypeId", lt.getId());
            typeBalance.put("leaveTypeCode", lt.getCode());
            typeBalance.put("leaveTypeName", lt.getName());
            typeBalance.put("accrualMode", lt.getAccrualMode().name());
            typeBalance.put("isPaid", lt.getIsPaid());

            // Calculate balances based on accrual mode
            if (lt.getAccrualMode() == AccrualMode.MONTHLY || lt.getAccrualMode() == AccrualMode.HYBRID) {
                // For monthly accrual, show month-wise breakdown
                int targetMonth = (year == currentYear) ? currentMonth : 12;
                LeaveLedger ledger = getOrCreateLedger(orgId, empId, lt.getId(), year, targetMonth);
                
                typeBalance.put("monthlyOpening", ledger.getOpeningMonthly());
                typeBalance.put("monthlyAccrued", ledger.getAccruedMonthly());
                typeBalance.put("monthlyUsed", ledger.getUsedFromMonthly());
                typeBalance.put("monthlyBalance", ledger.getClosingMonthly());
            }

            // Always show annual balance
            EmployeeLeaveAllocation alloc = getOrCreateAllocation(orgId, empId, lt.getId(), year);
            BigDecimal annualAvailable = calculateAnnualAvailable(alloc);
            
            typeBalance.put("annualOpening", alloc.getOpeningAnnual());
            typeBalance.put("annualAccrued", alloc.getAccruedAnnual());
            typeBalance.put("annualUsed", alloc.getUsedFromAnnual());
            typeBalance.put("annualBalance", annualAvailable);
            typeBalance.put("yearLocked", alloc.getYearLocked());

            // Calculate total available
            BigDecimal monthlyBal = BigDecimal.ZERO;
            if (typeBalance.get("monthlyBalance") != null) {
                monthlyBal = (BigDecimal) typeBalance.get("monthlyBalance");
            }
            typeBalance.put("totalAvailable", monthlyBal.add(annualAvailable));

            // Calculate total used this year from leaves
            BigDecimal totalUsed = leaveRepo.sumTotalDaysByYear(orgId, empId, lt.getId(), CONSUMED_STATUSES, year);
            typeBalance.put("totalUsedThisYear", totalUsed != null ? totalUsed : BigDecimal.ZERO);

            balances.add(typeBalance);
        }

        result.put("balances", balances);
        return result;
    }

    /**
     * Accrue leave at the start of a month (for monthly accrual types)
     */
    @Override
    public void accrueAtMonthStart(String orgId, String empId, Long leaveTypeId, int year, int month) {
        LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElseThrow();
        
        if (lt.getAccrualMode() == AccrualMode.ANNUAL) {
            // Annual accrual happens at year start, not monthly
            return;
        }

        LeaveLedger ledger = getOrCreateLedger(orgId, empId, leaveTypeId, year, month);
        
        if (ledger.getLockState() == LockState.LOCKED) {
            return; // Already processed
        }

        // Get previous month's closing as this month's opening
        BigDecimal opening = BigDecimal.ZERO;
        if (month > 1) {
            LeaveLedger prevLedger = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                    orgId, empId, leaveTypeId, year, month - 1).orElse(null);
            if (prevLedger != null) {
                opening = prevLedger.getClosingMonthly();
            }
        } else {
            // January - get carry forward from previous year's December
            LeaveLedger prevYearLedger = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                    orgId, empId, leaveTypeId, year - 1, 12).orElse(null);
            if (prevYearLedger != null) {
                opening = calculateCarryForward(lt, prevYearLedger.getClosingMonthly());
            }
        }

        // Calculate monthly accrual
        BigDecimal accrual = lt.getMonthlyQuotaDays() != null ? lt.getMonthlyQuotaDays() : BigDecimal.ZERO;

        ledger.setOpeningMonthly(opening);
        ledger.setAccruedMonthly(accrual);
        recalculateClosing(ledger);
        
        ledgerRepo.save(ledger);
    }

    /**
     * Apply consumption when a leave is marked
     */
    @Override
    public void applyConsumption(String orgId, String empId, Long leaveTypeId, 
                                  int year, int month, BigDecimal days, ConsumedFrom from) {
        LeaveLedger ledger = getOrCreateLedger(orgId, empId, leaveTypeId, year, month);
        EmployeeLeaveAllocation allocation = getOrCreateAllocation(orgId, empId, leaveTypeId, year);

        if (from == ConsumedFrom.MONTHLY) {
            ledger.setUsedFromMonthly(ledger.getUsedFromMonthly().add(days));
            recalculateClosing(ledger);
            ledgerRepo.save(ledger);
        } else if (from == ConsumedFrom.ANNUAL) {
            allocation.setUsedFromAnnual(allocation.getUsedFromAnnual().add(days));
            recalculateAnnualClosing(allocation);
            allocationRepo.save(allocation);
        } else {
            // MIXED - first consume from monthly, then annual
            LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElseThrow();
            ConsumptionBreakdown breakdown = calculateConsumptionBreakdown(
                    lt, ledger.getClosingMonthly(), calculateAnnualAvailable(allocation), days);
            
            if (breakdown.fromMonthly.compareTo(BigDecimal.ZERO) > 0) {
                ledger.setUsedFromMonthly(ledger.getUsedFromMonthly().add(breakdown.fromMonthly));
                recalculateClosing(ledger);
                ledgerRepo.save(ledger);
            }
            if (breakdown.fromAnnual.compareTo(BigDecimal.ZERO) > 0) {
                allocation.setUsedFromAnnual(allocation.getUsedFromAnnual().add(breakdown.fromAnnual));
                recalculateAnnualClosing(allocation);
                allocationRepo.save(allocation);
            }
        }
    }

    /**
     * Initialize annual allocation for an employee (called at year start or first leave request)
     */
    public void initializeAnnualAllocation(String orgId, String empId, Long leaveTypeId, int year) {
        EmployeeLeaveAllocation existing = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                orgId, empId, leaveTypeId, year).orElse(null);
        
        if (existing != null) return; // Already initialized

        LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElseThrow();
        
        EmployeeLeaveAllocation alloc = new EmployeeLeaveAllocation();
        alloc.setOrgId(orgId);
        alloc.setEmpId(empId);
        alloc.setLeaveType(lt);
        alloc.setLeaveYear(year);

        // Check for carry forward from previous year
        BigDecimal opening = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(lt.getAnnualCfAllowed())) {
            EmployeeLeaveAllocation prevAlloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                    orgId, empId, leaveTypeId, year - 1).orElse(null);
            if (prevAlloc != null) {
                BigDecimal cfAmount = calculateAnnualAvailable(prevAlloc);
                if (lt.getAnnualCfCapDays() != null) {
                    cfAmount = cfAmount.min(lt.getAnnualCfCapDays());
                }
                opening = cfAmount;
            }
        }

        alloc.setOpeningAnnual(opening);
        alloc.setAccruedAnnual(lt.getAnnualAllocationDays() != null ? lt.getAnnualAllocationDays() : BigDecimal.ZERO);
        alloc.setUsedFromAnnual(BigDecimal.ZERO);
        alloc.setCarriedForwardOut(BigDecimal.ZERO);
        alloc.setExpiredAnnual(BigDecimal.ZERO);
        recalculateAnnualClosing(alloc);
        
        allocationRepo.save(alloc);
    }

    // ============ Helper Methods ============

    private LeaveLedger getOrCreateLedger(String orgId, String empId, Long leaveTypeId, int year, int month) {
        return ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                orgId, empId, leaveTypeId, year, month)
            .orElseGet(() -> createNewLedger(orgId, empId, leaveTypeId, year, month));
    }

    private LeaveLedger createNewLedger(String orgId, String empId, Long leaveTypeId, int year, int month) {
        LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElseThrow();
        
        LeaveLedger ledger = new LeaveLedger();
        ledger.setOrgId(orgId);
        ledger.setEmpId(empId);
        ledger.setLeaveType(lt);
        ledger.setLeaveYear(year);
        ledger.setLeaveMonth(month);
        ledger.setLockState(LockState.OPEN);

        // Calculate opening from previous month
        BigDecimal opening = BigDecimal.ZERO;
        if (month > 1) {
            LeaveLedger prev = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                    orgId, empId, leaveTypeId, year, month - 1).orElse(null);
            if (prev != null) {
                opening = prev.getClosingMonthly();
            }
        } else if (year > 2020) { // Avoid going too far back
            LeaveLedger prevYearDec = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
                    orgId, empId, leaveTypeId, year - 1, 12).orElse(null);
            if (prevYearDec != null) {
                opening = calculateCarryForward(lt, prevYearDec.getClosingMonthly());
            }
        }

        // Calculate accrual
        BigDecimal accrual = BigDecimal.ZERO;
        if (lt.getAccrualMode() == AccrualMode.MONTHLY || lt.getAccrualMode() == AccrualMode.HYBRID) {
            accrual = lt.getMonthlyQuotaDays() != null ? lt.getMonthlyQuotaDays() : BigDecimal.ZERO;
        }

        // Calculate used this month from actual leaves
        BigDecimal used = leaveRepo.sumTotalDaysByMonth(orgId, empId, leaveTypeId, CONSUMED_STATUSES, year, month);
        if (used == null) used = BigDecimal.ZERO;

        ledger.setOpeningMonthly(opening);
        ledger.setAccruedMonthly(accrual);
        ledger.setUsedFromMonthly(used);
        ledger.setMovedToAnnual(BigDecimal.ZERO);
        ledger.setExpiredMonthly(BigDecimal.ZERO);
        recalculateClosing(ledger);

        return ledgerRepo.save(ledger);
    }

    private EmployeeLeaveAllocation getOrCreateAllocation(String orgId, String empId, Long leaveTypeId, int year) {
        return allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(orgId, empId, leaveTypeId, year)
            .orElseGet(() -> createNewAllocation(orgId, empId, leaveTypeId, year));
    }

    private EmployeeLeaveAllocation createNewAllocation(String orgId, String empId, Long leaveTypeId, int year) {
        LeaveType lt = leaveTypeRepo.findById(leaveTypeId).orElseThrow();
        
        EmployeeLeaveAllocation alloc = new EmployeeLeaveAllocation();
        alloc.setOrgId(orgId);
        alloc.setEmpId(empId);
        alloc.setLeaveType(lt);
        alloc.setLeaveYear(year);

        // Check for carry forward from previous year
        BigDecimal opening = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(lt.getAnnualCfAllowed())) {
            EmployeeLeaveAllocation prevAlloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                    orgId, empId, leaveTypeId, year - 1).orElse(null);
            if (prevAlloc != null) {
                BigDecimal cfAmount = calculateAnnualAvailable(prevAlloc);
                if (lt.getAnnualCfCapDays() != null) {
                    cfAmount = cfAmount.min(lt.getAnnualCfCapDays());
                }
                opening = cfAmount;
            }
        }

        // Calculate annual allocation
        BigDecimal annualAlloc = BigDecimal.ZERO;
        if (lt.getAccrualMode() == AccrualMode.ANNUAL || lt.getAccrualMode() == AccrualMode.HYBRID) {
            annualAlloc = lt.getAnnualAllocationDays() != null ? lt.getAnnualAllocationDays() : BigDecimal.ZERO;
        }

        // Calculate used this year from actual leaves
        BigDecimal used = leaveRepo.sumTotalDaysByYear(orgId, empId, leaveTypeId, CONSUMED_STATUSES, year);
        if (used == null) used = BigDecimal.ZERO;

        alloc.setOpeningAnnual(opening);
        alloc.setAccruedAnnual(annualAlloc);
        alloc.setUsedFromAnnual(used);
        alloc.setCarriedForwardOut(BigDecimal.ZERO);
        alloc.setExpiredAnnual(BigDecimal.ZERO);
        alloc.setYearLocked(false);
        recalculateAnnualClosing(alloc);

        return allocationRepo.save(alloc);
    }

    private void recalculateClosing(LeaveLedger ledger) {
        BigDecimal closing = ledger.getOpeningMonthly()
                .add(ledger.getAccruedMonthly())
                .subtract(ledger.getUsedFromMonthly())
                .subtract(ledger.getMovedToAnnual())
                .subtract(ledger.getExpiredMonthly());
        ledger.setClosingMonthly(closing.max(BigDecimal.ZERO));
    }

    private void recalculateAnnualClosing(EmployeeLeaveAllocation alloc) {
        BigDecimal closing = alloc.getOpeningAnnual()
                .add(alloc.getAccruedAnnual())
                .subtract(alloc.getUsedFromAnnual())
                .subtract(alloc.getCarriedForwardOut())
                .subtract(alloc.getExpiredAnnual());
        alloc.setClosingAnnual(closing.max(BigDecimal.ZERO));
    }

    private BigDecimal calculateAnnualAvailable(EmployeeLeaveAllocation alloc) {
        return alloc.getOpeningAnnual()
                .add(alloc.getAccruedAnnual())
                .subtract(alloc.getUsedFromAnnual())
                .subtract(alloc.getCarriedForwardOut())
                .subtract(alloc.getExpiredAnnual())
                .max(BigDecimal.ZERO);
    }

    private BigDecimal calculateCarryForward(LeaveType lt, BigDecimal balance) {
        if (lt.getMonthlyCfBehavior() == null || lt.getMonthlyCfBehavior() == MonthlyCfBehavior.EXPIRE) {
            return BigDecimal.ZERO;
        }
        if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.ROLLOVER_MONTH) {
            return balance;
        }
        if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.ACCUMULATE_TO_ANNUAL) {
            // For accumulate to annual, the balance moves to annual pool, so monthly CF is 0
            return BigDecimal.ZERO;
        }
        if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.NONE) {
            return balance; // Keep balance as is
        }
        return BigDecimal.ZERO;
    }

    private ConsumptionBreakdown calculateConsumptionBreakdown(LeaveType lt, 
            BigDecimal monthlyAvailable, BigDecimal annualAvailable, BigDecimal totalDays) {
        
        BigDecimal fromMonthly = BigDecimal.ZERO;
        BigDecimal fromAnnual = BigDecimal.ZERO;

        ConsumeOrder order = lt.getConsumeOrder() != null ? lt.getConsumeOrder() : ConsumeOrder.MONTHLY_THEN_ANNUAL;
        
        if (order == ConsumeOrder.MONTHLY_THEN_ANNUAL) {
            fromMonthly = totalDays.min(monthlyAvailable);
            BigDecimal remaining = totalDays.subtract(fromMonthly);
            fromAnnual = remaining.min(annualAvailable);
        } else {
            fromAnnual = totalDays.min(annualAvailable);
            BigDecimal remaining = totalDays.subtract(fromAnnual);
            fromMonthly = remaining.min(monthlyAvailable);
        }

        return new ConsumptionBreakdown(fromMonthly, fromAnnual);
    }

    // ============ Inner Classes ============

    public record BalanceResult(BigDecimal monthlyBalance, BigDecimal annualBalance, BigDecimal totalBalance) {}
    
    private record ConsumptionBreakdown(BigDecimal fromMonthly, BigDecimal fromAnnual) {}
}
