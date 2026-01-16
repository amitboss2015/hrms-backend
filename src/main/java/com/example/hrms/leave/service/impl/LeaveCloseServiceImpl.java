package com.example.hrms.leave.service.impl;

import com.example.hrms.leave.domain.EmployeeLeaveAllocation;
import com.example.hrms.leave.domain.LeaveLedger;
import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.domain.enums.LockState;
import com.example.hrms.leave.domain.enums.MonthlyCfBehavior;
import com.example.hrms.leave.repo.EmployeeLeaveAllocationRepository;
import com.example.hrms.leave.repo.LeaveLedgerRepository;
import com.example.hrms.leave.repo.LeaveTypeRepository;
import com.example.hrms.leave.service.LeaveCloseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class LeaveCloseServiceImpl implements LeaveCloseService {

    private final LeaveLedgerRepository ledgerRepo;
    private final EmployeeLeaveAllocationRepository allocationRepo;
    private final LeaveTypeRepository leaveTypeRepo;

    public LeaveCloseServiceImpl(LeaveLedgerRepository ledgerRepo,
                                  EmployeeLeaveAllocationRepository allocationRepo,
                                  LeaveTypeRepository leaveTypeRepo) {
        this.ledgerRepo = ledgerRepo;
        this.allocationRepo = allocationRepo;
        this.leaveTypeRepo = leaveTypeRepo;
    }

    /**
     * Close a month for leave processing.
     * This involves:
     * 1. Calculate carry-forward or expiry based on leave type rules
     * 2. Move excess to annual pool if applicable
     * 3. Lock the month ledger
     */
    @Override
    public void closeMonth(String orgId, int year, int month) {
        // Get all ledgers for this org and month
        List<LeaveLedger> ledgers = ledgerRepo.findByOrgIdAndEmpIdAndLeaveYear(orgId, null, year);
        
        // Filter to only this month's ledgers (the method returns all months for the year)
        // We need to process each unique employee+leaveType combination
        List<LeaveType> leaveTypes = leaveTypeRepo.findByOrgIdAndActiveTrue(orgId);
        
        for (LeaveType lt : leaveTypes) {
            // Find all ledgers for this leave type and month
            List<LeaveLedger> typeLedgers = ledgerRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                    orgId, null, lt.getId(), year);
            
            for (LeaveLedger ledger : typeLedgers) {
                if (ledger.getLeaveMonth() != month) continue;
                if (ledger.getLockState() == LockState.LOCKED) continue;
                
                processMonthEnd(ledger, lt);
                ledger.setLockState(LockState.LOCKED);
                ledgerRepo.save(ledger);
            }
        }
    }

    /**
     * Close a year for leave processing.
     * This involves:
     * 1. Calculate annual carry-forward based on leave type rules
     * 2. Apply any expiry
     * 3. Lock the year allocation
     */
    @Override
    public void closeYear(String orgId, int year) {
        List<EmployeeLeaveAllocation> allocations = allocationRepo.findByOrgIdAndLeaveYear(orgId, year);
        
        for (EmployeeLeaveAllocation alloc : allocations) {
            if (Boolean.TRUE.equals(alloc.getYearLocked())) continue;
            
            LeaveType lt = alloc.getLeaveType();
            processYearEnd(alloc, lt, year);
            alloc.setYearLocked(true);
            allocationRepo.save(alloc);
        }
    }

    // ============ Helper Methods ============

    private void processMonthEnd(LeaveLedger ledger, LeaveType lt) {
        BigDecimal balance = ledger.getClosingMonthly();
        
        if (lt.getMonthlyCfBehavior() == null || lt.getMonthlyCfBehavior() == MonthlyCfBehavior.EXPIRE) {
            // All unused monthly balance expires
            ledger.setExpiredMonthly(balance);
            ledger.setClosingMonthly(BigDecimal.ZERO);
        } else if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.ACCUMULATE_TO_ANNUAL) {
            // Move to annual pool
            ledger.setMovedToAnnual(balance);
            ledger.setClosingMonthly(BigDecimal.ZERO);
            
            // Add to annual allocation
            EmployeeLeaveAllocation alloc = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                    ledger.getOrgId(), ledger.getEmpId(), lt.getId(), ledger.getLeaveYear()).orElse(null);
            if (alloc != null) {
                alloc.setAccruedAnnual(alloc.getAccruedAnnual().add(balance));
                recalculateAnnualClosing(alloc);
                allocationRepo.save(alloc);
            }
        } else if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.ROLLOVER_MONTH) {
            // Full carry forward - balance stays as is (rolls over to next month)
        } else if (lt.getMonthlyCfBehavior() == MonthlyCfBehavior.NONE) {
            // No special behavior - balance stays as is
        }
    }

    private void processYearEnd(EmployeeLeaveAllocation alloc, LeaveType lt, int year) {
        BigDecimal balance = calculateAnnualAvailable(alloc);
        
        if (!Boolean.TRUE.equals(lt.getAnnualCfAllowed())) {
            // No carry forward - all expires
            alloc.setExpiredAnnual(balance);
            recalculateAnnualClosing(alloc);
        } else {
            // Carry forward allowed - check cap
            BigDecimal cap = lt.getAnnualCfCapDays();
            if (cap != null && balance.compareTo(cap) > 0) {
                BigDecimal excess = balance.subtract(cap);
                alloc.setExpiredAnnual(excess);
                alloc.setCarriedForwardOut(cap);
            } else {
                alloc.setCarriedForwardOut(balance);
            }
            recalculateAnnualClosing(alloc);
            
            // Create next year's allocation with carry forward
            createNextYearAllocation(alloc, lt, year + 1);
        }
    }

    private void createNextYearAllocation(EmployeeLeaveAllocation currentAlloc, LeaveType lt, int nextYear) {
        // Check if next year allocation already exists
        EmployeeLeaveAllocation existing = allocationRepo.findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
                currentAlloc.getOrgId(), currentAlloc.getEmpId(), lt.getId(), nextYear).orElse(null);
        
        if (existing != null) {
            // Update opening with carry forward
            existing.setOpeningAnnual(currentAlloc.getCarriedForwardOut());
            recalculateAnnualClosing(existing);
            allocationRepo.save(existing);
        } else {
            // Create new allocation
            EmployeeLeaveAllocation newAlloc = new EmployeeLeaveAllocation();
            newAlloc.setOrgId(currentAlloc.getOrgId());
            newAlloc.setEmpId(currentAlloc.getEmpId());
            newAlloc.setLeaveType(lt);
            newAlloc.setLeaveYear(nextYear);
            newAlloc.setOpeningAnnual(currentAlloc.getCarriedForwardOut());
            newAlloc.setAccruedAnnual(lt.getAnnualAllocationDays() != null ? lt.getAnnualAllocationDays() : BigDecimal.ZERO);
            newAlloc.setUsedFromAnnual(BigDecimal.ZERO);
            newAlloc.setCarriedForwardOut(BigDecimal.ZERO);
            newAlloc.setExpiredAnnual(BigDecimal.ZERO);
            newAlloc.setYearLocked(false);
            recalculateAnnualClosing(newAlloc);
            allocationRepo.save(newAlloc);
        }
    }

    private BigDecimal calculateAnnualAvailable(EmployeeLeaveAllocation alloc) {
        return alloc.getOpeningAnnual()
                .add(alloc.getAccruedAnnual())
                .subtract(alloc.getUsedFromAnnual())
                .subtract(alloc.getCarriedForwardOut())
                .subtract(alloc.getExpiredAnnual())
                .max(BigDecimal.ZERO);
    }

    private void recalculateAnnualClosing(EmployeeLeaveAllocation alloc) {
        BigDecimal closing = alloc.getOpeningAnnual()
                .add(alloc.getAccruedAnnual())
                .subtract(alloc.getUsedFromAnnual())
                .subtract(alloc.getCarriedForwardOut())
                .subtract(alloc.getExpiredAnnual())
                .max(BigDecimal.ZERO);
        alloc.setClosingAnnual(closing);
    }
}
